package dk.broegger_kruse.applicationdemo.services;

import dk.broegger_kruse.applicationdemo.entities.ModPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
    private static final int DEFAULT_XMX_MIB = 8192;
    private static final int XMX_OVERHEAD_MIB = 250;
    private static final Pattern XMX_PATTERN = Pattern.compile("(?i)^(?:-Xmx)?(\\d+)([mMgG])$");

    private final Path modpacksRoot;
    private final Path tempRoot;
    private final ResourceLoader resourceLoader;

    public FileService(
            @Value("${app.storage.modpacks-root:modpacks}") String modpacksRootPath,
            @Value("${app.storage.temp-root:${java.io.tmpdir}/application-demo}") String tempRootPath,
            ResourceLoader resourceLoader
    ) {
        this.modpacksRoot = Path.of(modpacksRootPath).toAbsolutePath().normalize();
        this.tempRoot = Path.of(tempRootPath).toAbsolutePath().normalize();
        this.resourceLoader = resourceLoader;
    }

    public ModPack createDraftModPackFromFile(MultipartFile file) {
        Objects.requireNonNull(file, "file must not be null");

        var originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown-modpack.zip");
        var normalizedName = originalName.replace('\\', '/');
        var fileName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
        var inferredName = sanitizePackName(stripExtension(fileName));
        var packDirectory = modpacksRoot.resolve(inferredName).normalize();

        if (Files.exists(packDirectory)) {
            throw new IllegalStateException("A modpack with name '" + inferredName + "' already exists.");
        }

        var archivePath = tempRoot.resolve(UUID.randomUUID() + "-" + fileName).normalize();

        logger.info("Preparing draft modpack from archive originalName='{}', inferredName='{}'.", originalName, inferredName);

        try {
            Files.createDirectories(modpacksRoot);
            Files.createDirectories(tempRoot);
            Files.createDirectories(packDirectory);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, archivePath, StandardCopyOption.REPLACE_EXISTING);
            }

            unzipArchive(archivePath, packDirectory);
            copyTemplateFile("eula.txt", packDirectory.resolve("eula.txt"));
            copyTemplateFile("server.properties", packDirectory.resolve("server.properties"));
            ensureJvmArgsExists(packDirectory);
            logger.info("Archive extracted to '{}'.", packDirectory);
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

        var entryPoint = resolveEntryPoint(packDirectory);
        var javaXmx = resolveJavaXmx(packDirectory.resolve("user_jvm_args.txt"));
        var now = Instant.now();

        return new ModPack(
                inferredName,
                packDirectory.toString(),
                DEFAULT_PACK_VERSION,
                DEFAULT_MINECRAFT_VERSION,
                DEFAULT_JAVA_VERSION,
                javaXmx,
                DEFAULT_PORT,
                entryPoint,
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

    public void renamePackDirectoryToManagedName(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        if (modPack.getPackId() == null) {
            throw new IllegalStateException("Pack ID is required before assigning managed directory name.");
        }

        var currentPath = resolvePackPath(modPack);
        var targetName = buildManagedDirectoryName(modPack);
        var targetPath = modpacksRoot.resolve(targetName).normalize();

        if (currentPath.equals(targetPath)) {
            modPack.setPath(targetPath.toString());
            logger.debug("Pack directory already matches managed name for packId={}: {}", modPack.getPackId(), targetPath);
            return;
        }

        if (Files.exists(targetPath)) {
            throw new IllegalStateException("Managed modpack directory already exists: " + targetPath);
        }

        try {
            Files.createDirectories(Objects.requireNonNullElse(targetPath.getParent(), modpacksRoot));
            Files.move(currentPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Renamed modpack directory for packId={} from '{}' to '{}'.", modPack.getPackId(), currentPath, targetPath);
        } catch (IOException atomicMoveException) {
            try {
                Files.move(currentPath, targetPath);
                logger.info("Renamed modpack directory without atomic move for packId={} from '{}' to '{}'.", modPack.getPackId(), currentPath, targetPath);
            } catch (IOException moveException) {
                logger.error("Failed renaming modpack directory for packId={} from '{}' to '{}'.", modPack.getPackId(), currentPath, targetPath, moveException);
                throw new IllegalStateException("Failed to rename modpack directory to managed name.", moveException);
            }
        }

        modPack.setPath(targetPath.toString());
    }

    private void unzipArchive(Path archivePath, Path outputDir) throws IOException {
        try (InputStream inputStream = Files.newInputStream(archivePath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                var entryPath = outputDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(outputDir)) {
                    throw new IllegalStateException("Blocked zip entry outside target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(Objects.requireNonNullElse(entryPath.getParent(), outputDir));
                    Files.copy(zipInputStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zipInputStream.closeEntry();
            }
        }
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

    private String sanitizePackName(String name) {
        var trimmed = name == null ? "" : name.trim();
        var cleaned = trimmed.replaceAll("[^a-zA-Z0-9._-]", "-").replaceAll("-+", "-");
        return cleaned.isBlank() ? "modpack" : cleaned;
    }

    private String resolveEntryPoint(Path packDirectory) {
        var supportedCandidates = List.of("startserver.sh", "run.sh", "start.sh", DEFAULT_ENTRYPOINT);
        for (var candidate : supportedCandidates) {
            if (Files.exists(packDirectory.resolve(candidate))) {
                return candidate;
            }
        }
        return DEFAULT_ENTRYPOINT;
    }

    private String stripExtension(String fileName) {
        var extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionIndex);
    }

    private String buildManagedDirectoryName(ModPack modPack) {
        var safeName = sanitizePackName(modPack.getName()).toLowerCase();
        var safeVersion = sanitizePackName(Objects.requireNonNullElse(modPack.getPackVersion(), DEFAULT_PACK_VERSION)).toLowerCase();
        return modPack.getPackId() + "-" + safeName + "-" + safeVersion;
    }
}

