package dk.broegger_kruse.mcmsm.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import dk.broegger_kruse.mcmsm.entities.ModPack;
import dk.broegger_kruse.mcmsm.entities.UserEntity;
import dk.broegger_kruse.mcmsm.util.ZipArchiveUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

@Service
public class FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private static final String DEFAULT_STATUS = "not_deployed";
    private static final String DEFAULT_PACK_VERSION = "unknown";
    private static final String DEFAULT_MINECRAFT_VERSION = "unknown";
    private static final Integer DEFAULT_JAVA_VERSION = 21;
    private static final String DEFAULT_JAVA_XMX = "5G";
    private static final String DEFAULT_PORT = "25565";
    private static final String DEFAULT_ENTRYPOINT = "startserver.sh";
    private static final String[] DEFAULT_ENTRYPOINT_CANDIDATES = new String[]{DEFAULT_ENTRYPOINT};
    private static final int DEFAULT_XMX_MIB = 8192;
    private static final int XMX_OVERHEAD_MIB = 250;
    private static final Pattern XMX_PATTERN = Pattern.compile("(?i)^(?:-Xmx)?(\\d+)([mMgG])$");
    private static final Pattern PACK_VERSION_PATTERN = Pattern.compile("(?:^|[-_])(\\d+(?:[.-]\\d+){2,})");
    private static final Type USER_LIST_TYPE = new TypeToken<List<UserEntity>>() { }.getType();
    private static final Type MODPACK_LIST_TYPE = new TypeToken<List<ModPack>>() { }.getType();

    private final Path modpacksRoot;
    private final Path tempRoot;
    private final Path metadataRoot;
    private final Path usersFile;
    private final Path modpacksMetadataFile;
    private final ResourceLoader resourceLoader;
    private final Gson gson;
    private final ReentrantReadWriteLock usersLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock modpacksLock = new ReentrantReadWriteLock();

    public FileService(
            @Value("${app.storage.modpacks-root:modpacks}") String modpacksRootPath,
            @Value("${app.storage.temp-root:${java.io.tmpdir}/application-demo}") String tempRootPath,
            @Value("${app.storage.metadata-root:data}") String metadataRootPath,
            ResourceLoader resourceLoader
    ) {
        this.modpacksRoot = Path.of(modpacksRootPath).toAbsolutePath().normalize();
        this.tempRoot = Path.of(tempRootPath).toAbsolutePath().normalize();
        this.metadataRoot = Path.of(metadataRootPath).toAbsolutePath().normalize();
        this.usersFile = this.metadataRoot.resolve("users.json");
        this.modpacksMetadataFile = this.metadataRoot.resolve("modpacks.json");
        this.resourceLoader = resourceLoader;
        JsonSerializer<Instant> instantSerializer = (src, typeOfSrc, context) -> new JsonPrimitive(src.toString());
        JsonDeserializer<Instant> instantDeserializer = (json, typeOfT, context) -> {
            try {
                return Instant.parse(json.getAsString());
            } catch (Exception ex) {
                throw new JsonParseException("Invalid Instant value: " + json, ex);
            }
        };

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, instantSerializer)
                .registerTypeAdapter(Instant.class, instantDeserializer)
                .create();

        initializeMetadataStore();
    }

    public List<UserEntity> getAllUsers() {
        return readUsers();
    }

    public Optional<UserEntity> findUserByUsername(String username) {
        return readUsers().stream()
                .filter(user -> Objects.equals(user.getUsername(), username))
                .findFirst();
    }

    public Optional<UserEntity> findUserByUsernameAndPassword(String username, String password) {
        return readUsers().stream()
                .filter(user -> Objects.equals(user.getUsername(), username) && Objects.equals(user.getPassword(), password))
                .findFirst();
    }

    public boolean existsByUsername(String username) {
        return findUserByUsername(username).isPresent();
    }

    public UserEntity saveUser(UserEntity userEntity) {
        Objects.requireNonNull(userEntity, "userEntity must not be null");

        var lock = usersLock.writeLock();
        lock.lock();
        try {
            List<UserEntity> users = this.readJsonList(usersFile, USER_LIST_TYPE);
            if (userEntity.getId() == null) {
                userEntity.setId(nextUserId(users));
                users.add(userEntity);
            } else {
                var replaced = false;
                for (var index = 0; index < users.size(); index++) {
                    if (Objects.equals(users.get(index).getId(), userEntity.getId())) {
                        users.set(index, userEntity);
                        replaced = true;
                        break;
                    }
                }

                if (!replaced) {
                    users.add(userEntity);
                }
            }

            writeJsonList(usersFile, users);
            return userEntity;
        } finally {
            lock.unlock();
        }
    }

    public List<ModPack> getAllModPacks() {
        return readModPacks();
    }

    public List<ModPack> getAllModPacksByDeployment(boolean isDeployed) {
        return readModPacks().stream()
                .filter(pack -> Objects.equals(Boolean.TRUE.equals(pack.getIsDeployed()), isDeployed))
                .toList();
    }

    public Optional<ModPack> findModPackById(Long packId) {
        return readModPacks().stream()
                .filter(pack -> Objects.equals(pack.getPackId(), packId))
                .findFirst();
    }

    public boolean existsModPackByName(String name) {
        return readModPacks().stream().anyMatch(pack -> Objects.equals(pack.getName(), name));
    }

    public ModPack saveModPack(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");

        var lock = modpacksLock.writeLock();
        lock.lock();
        try {
            List<ModPack> modPacks = this.<ModPack>readJsonList(modpacksMetadataFile, MODPACK_LIST_TYPE);
            if (modPack.getPackId() == null) {
                modPack.setPackId(nextModPackId(modPacks));
                modPacks.add(modPack);
            } else {
                var replaced = false;
                for (var index = 0; index < modPacks.size(); index++) {
                    if (Objects.equals(modPacks.get(index).getPackId(), modPack.getPackId())) {
                        modPacks.set(index, modPack);
                        replaced = true;
                        break;
                    }
                }

                if (!replaced) {
                    modPacks.add(modPack);
                }
            }

            writeJsonList(modpacksMetadataFile, modPacks);
            return modPack;
        } finally {
            lock.unlock();
        }
    }

    public void deleteModPack(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        deleteModPackById(modPack.getPackId());
    }

    public void deleteModPackById(Long packId) {
        var lock = modpacksLock.writeLock();
        lock.lock();
        try {
            List<ModPack> modPacks = this.<ModPack>readJsonList(modpacksMetadataFile, MODPACK_LIST_TYPE);
            modPacks.removeIf(pack -> Objects.equals(pack.getPackId(), packId));
            writeJsonList(modpacksMetadataFile, modPacks);
        } finally {
            lock.unlock();
        }
    }

    public void deleteAllModPacks(List<ModPack> stalePacks) {
        var staleIds = stalePacks.stream().map(ModPack::getPackId).toList();

        var lock = modpacksLock.writeLock();
        lock.lock();
        try {
            List<ModPack> modPacks = this.<ModPack>readJsonList(modpacksMetadataFile, MODPACK_LIST_TYPE);
            modPacks.removeIf(pack -> staleIds.contains(pack.getPackId()));
            writeJsonList(modpacksMetadataFile, modPacks);
        } finally {
            lock.unlock();
        }
    }

    public ModPack createDraftModPackFromFile(MultipartFile file) {
        Objects.requireNonNull(file, "file must not be null");

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown-modpack.zip");
        String normalizedName = originalName.replace('\\', '/');
        String fileName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
        String packVersion = inferPackVersion(fileName);
        String inferredName = sanitizePackName(stripExtension(fileName), packVersion);
        Path stagingDirectory = modpacksRoot.resolve("draft-" + UUID.randomUUID()).normalize();

        Path archivePath = tempRoot.resolve(UUID.randomUUID() + "-" + fileName).normalize();

        logger.info("Preparing draft modpack from archive originalName='{}', inferredName='{}'.", originalName, inferredName);

        try {
            Files.createDirectories(modpacksRoot);
            Files.createDirectories(tempRoot);
            Files.createDirectories(stagingDirectory);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, archivePath, StandardCopyOption.REPLACE_EXISTING);
            }

            ZipArchiveUtil.extractArchiveToDirectory(archivePath, stagingDirectory);
            copyTemplateFile("eula.txt", stagingDirectory.resolve("eula.txt"));
            copyTemplateFile("server.properties", stagingDirectory.resolve("server.properties"));
            ensureJvmArgsExists(stagingDirectory);
            logger.info("Archive extracted to staging directory '{}'.", stagingDirectory);
        } catch (IOException e) {
            logger.error("Failed processing uploaded archive originalName='{}'.", originalName, e);
            throw new IllegalStateException("Failed to process uploaded modpack archive.", e);
        } finally {
            try {
                Files.deleteIfExists(archivePath);
            } catch (IOException ignored) {
                // Non-fatal cleanup failure for temporary archive.
                logger.warn("Failed to clean temporary archive '{}'.", archivePath);
            }
        }

        var entryPointCandidates = resolveEntryPointCandidates(stagingDirectory);
        var entryPoint = resolveEntryPoint(entryPointCandidates);
        var javaXmx = resolveJavaXmx(stagingDirectory.resolve("user_jvm_args.txt"));
        var now = Instant.now();

        return new ModPack(
                inferredName,
                stagingDirectory.toString(),
                packVersion,
                DEFAULT_MINECRAFT_VERSION,
                DEFAULT_JAVA_VERSION,
                javaXmx,
                DEFAULT_PORT,
                entryPoint,
                entryPointCandidates,
                null,
                null,
                DEFAULT_STATUS,
                null,
                null,
                now
        );
    }

    public void syncServerPortWithMetadata(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");

        var serverPropertiesPath = resolvePackPath(modPack).resolve("server.properties");
        var requestedPort = Objects.requireNonNullElse(modPack.getPort(), DEFAULT_PORT).trim();

        try {
            var lines = Files.readAllLines(serverPropertiesPath);
            var expectedLine = "server-port=" + requestedPort;
            var updatedLines = lines.stream()
                    .map(line -> line.startsWith("server-port=") ? expectedLine : line)
                    .toList();

            var hasPortLine = updatedLines.stream().anyMatch(line -> line.startsWith("server-port="));
            var linesToWrite = hasPortLine ? updatedLines : Stream.concat(updatedLines.stream(), Stream.of(expectedLine)).toList();
            Files.write(serverPropertiesPath, linesToWrite, CREATE, TRUNCATE_EXISTING);
            logger.info("Synchronized server port for packId={} to {}.", modPack.getPackId(), requestedPort);
        } catch (IOException e) {
            logger.error("Failed to synchronize server.properties for packId={}", modPack.getPackId(), e);
            throw new IllegalStateException("Failed to synchronize server.properties for modpack '" + modPack.getName() + "'.", e);
        }
    }

    public int resolveContainerMemoryLimitMiB(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        var xmxMiB = parseXmxMiB(Objects.requireNonNullElse(modPack.getJavaXmx(), DEFAULT_JAVA_XMX)).orElse(DEFAULT_XMX_MIB);
        var limitMiB = xmxMiB + XMX_OVERHEAD_MIB;
        logger.debug("Resolved memory limit for packId={}: xmxMiB={}, overheadMiB={}, limitMiB={}", modPack.getPackId(), xmxMiB, XMX_OVERHEAD_MIB, limitMiB);
        return limitMiB;
    }

    public boolean packDirectoryExists(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        if (!hasText(modPack.getPath())) {
            return false;
        }

        try {
            var packPath = Path.of(modPack.getPath()).toAbsolutePath().normalize();
            return Files.isDirectory(packPath);
        } catch (Exception ignored) {
            return false;
        }
    }

    public void deletePackDirectory(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        var packPath = resolvePackPath(modPack);

        if (!Files.exists(packPath)) {
            logger.warn("Skipping directory delete for packId={} because path does not exist: {}", modPack.getPackId(), packPath);
            return;
        }

        try (Stream<Path> stream = Files.walk(packPath)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed deleting path: " + path, e);
                        }
                    });
            logger.info("Deleted modpack directory for packId={} at '{}'.", modPack.getPackId(), packPath);
        } catch (IOException e) {
            logger.error("Failed deleting modpack directory for packId={} at '{}'.", modPack.getPackId(), packPath, e);
            throw new IllegalStateException("Failed deleting modpack directory for '" + modPack.getName() + "'.", e);
        }
    }

    public void assignImmutablePackDirectoryPath(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        if (modPack.getPackId() == null) {
            throw new IllegalStateException("Pack ID is required before assigning immutable directory path.");
        }

        var currentPath = resolvePackPath(modPack);
        var targetPath = modpacksRoot.resolve(String.valueOf(modPack.getPackId())).normalize();

        if (currentPath.equals(targetPath)) {
            modPack.setPath(targetPath.toString());
            logger.debug("Pack directory already matches immutable path for packId={}: {}", modPack.getPackId(), targetPath);
            return;
        }

        if (Files.exists(targetPath)) {
            throw new IllegalStateException("Managed modpack directory already exists: " + targetPath);
        }

        try {
            Files.createDirectories(Objects.requireNonNullElse(targetPath.getParent(), modpacksRoot));
            Files.move(currentPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Moved modpack directory for packId={} from '{}' to immutable path '{}'.", modPack.getPackId(), currentPath, targetPath);
        } catch (IOException atomicMoveException) {
            try {
                Files.move(currentPath, targetPath);
                logger.info("Moved modpack directory without atomic move for packId={} from '{}' to immutable path '{}'.", modPack.getPackId(), currentPath, targetPath);
            } catch (IOException moveException) {
                logger.error("Failed moving modpack directory to immutable path for packId={} from '{}' to '{}'.", modPack.getPackId(), currentPath, targetPath, moveException);
                throw new IllegalStateException("Failed to assign immutable modpack directory path.", moveException);
            }
        }

        modPack.setPath(targetPath.toString());
    }

    private void initializeMetadataStore() {
        try {
            Files.createDirectories(metadataRoot);
            ensureJsonFileInitialized(usersFile);
            ensureJsonFileInitialized(modpacksMetadataFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed initializing file metadata store.", e);
        }
    }

    private void ensureJsonFileInitialized(Path filePath) throws IOException {
        if (Files.exists(filePath)) {
            return;
        }

        Files.createDirectories(Objects.requireNonNullElse(filePath.getParent(), metadataRoot));
        Files.writeString(filePath, "[]", CREATE, TRUNCATE_EXISTING);
    }

    private List<UserEntity> readUsers() {
        var lock = usersLock.readLock();
        lock.lock();
        try {
            return this.<UserEntity>readJsonList(usersFile, USER_LIST_TYPE);
        } finally {
            lock.unlock();
        }
    }

    private List<ModPack> readModPacks() {
        var lock = modpacksLock.readLock();
        lock.lock();
        try {
            return this.<ModPack>readJsonList(modpacksMetadataFile, MODPACK_LIST_TYPE);
        } finally {
            lock.unlock();
        }
    }

    private <T> List<T> readJsonList(Path filePath, Type listType) {
        try {
            ensureJsonFileInitialized(filePath);
            var raw = Files.readString(filePath, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return new ArrayList<>();
            }

            List<T> parsed = gson.fromJson(raw, listType);
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading metadata file: " + filePath, e);
        }
    }

    private <T> void writeJsonList(Path filePath, List<T> entries) {
        try {
            Files.createDirectories(Objects.requireNonNullElse(filePath.getParent(), metadataRoot));
            var tempFile = Files.createTempFile(metadataRoot, "store-", ".tmp");
            try {
                Files.writeString(tempFile, gson.toJson(entries), StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING);
                moveAtomicallyOrReplace(tempFile, filePath);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed writing metadata file: " + filePath, e);
        }
    }

    private void moveAtomicallyOrReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Long nextUserId(List<UserEntity> users) {
        return users.stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;
    }

    private Long nextModPackId(List<ModPack> modPacks) {
        return modPacks.stream()
                .map(ModPack::getPackId)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;
    }


    private void copyTemplateFile(String templateName, Path targetPath) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:templates/" + templateName);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing template resource: " + templateName);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureJvmArgsExists(Path packDirectory) throws IOException {
        var jvmArgsPath = packDirectory.resolve("user_jvm_args.txt");
        if (Files.exists(jvmArgsPath)) {
            return;
        }
        copyTemplateFile("user_jvm_args.txt", jvmArgsPath);
    }

    private Path resolvePackPath(ModPack modPack) {
        var packPath = Path.of(modPack.getPath()).toAbsolutePath().normalize();
        if (!Files.exists(packPath)) {
            throw new IllegalStateException("Modpack path does not exist: " + packPath);
        }
        return packPath;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveJavaXmx(Path jvmArgsPath) {
        try {
            var lines = Files.readAllLines(jvmArgsPath);
            var parsed = parseXmxToken(lines);
            return parsed.orElse(DEFAULT_JAVA_XMX);
        } catch (IOException e) {
            logger.warn("Could not resolve java xmx from '{}', using default {}.", jvmArgsPath, DEFAULT_JAVA_XMX);
            return DEFAULT_JAVA_XMX;
        }
    }

    private OptionalInt parseXmxMiB(String xmxToken) {
        var matcher = XMX_PATTERN.matcher(Objects.requireNonNullElse(xmxToken, "").trim());
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }

        var value = Integer.parseInt(matcher.group(1));
        var unit = matcher.group(2).toUpperCase();
        var valueMiB = "G".equals(unit) ? value * 1024 : value;
        return OptionalInt.of(valueMiB);
    }

    private Optional<String> parseXmxToken(List<String> lines) {
        for (String line : lines) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            var matcher = XMX_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }

            return Optional.of(matcher.group(1) + matcher.group(2).toUpperCase());
        }

        return Optional.empty();
    }

    private String sanitizePackName(String name, String packVersion) {
        if (name.isEmpty()) name = "";
        String noPackVersion = name.replace(packVersion, " ");
        String cleaned = noPackVersion.replaceAll("[^a-zA-Z]", " ");
        String cleaned2 = cleaned.replaceAll("\s{2,}", " ");
        return cleaned2.isBlank() ? "modpack" : cleaned2.trim();
    }

    private String inferPackVersion(String fileName) {
        if (!hasText(fileName)) {
            return DEFAULT_PACK_VERSION;
        }

        var matcher = PACK_VERSION_PATTERN.matcher(fileName);
        if (!matcher.find()) {
            return DEFAULT_PACK_VERSION;
        }

        // Normalize separators so versions are stored consistently.
        return matcher.group(1).replace('-', '.');
    }

    private String[] resolveEntryPointCandidates(Path packDirectory) {
        if (!Files.isDirectory(packDirectory)) {
            logger.warn("Pack directory does not exist or is not a directory: {}", packDirectory);
            return DEFAULT_ENTRYPOINT_CANDIDATES;
        }

        try (Stream<Path> entries = Files.list(packDirectory)) {
            var candidates = entries
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".sh"))
                    .sorted()
                    .toList();

            if (candidates.isEmpty()) {
                logger.warn("Did not find any .sh entry points in {}", packDirectory);
                return DEFAULT_ENTRYPOINT_CANDIDATES;
            }

            return candidates.toArray(String[]::new);
        } catch (IOException e) {
            logger.warn("Failed reading modpack root directory '{}', using default entrypoint.", packDirectory, e);
            return DEFAULT_ENTRYPOINT_CANDIDATES;
        }
    }

    private String resolveEntryPoint(String[] entryPointCandidates) {
        if (entryPointCandidates == null || entryPointCandidates.length == 0) {
            return DEFAULT_ENTRYPOINT;
        }

        return entryPointCandidates[0];
    }


    private String stripExtension(String fileName) {
        var extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionIndex);
    }

}

