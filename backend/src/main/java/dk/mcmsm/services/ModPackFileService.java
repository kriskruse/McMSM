package dk.mcmsm.services;

import dk.mcmsm.entities.ModPack;
import dk.mcmsm.entities.PackStatus;
import dk.mcmsm.services.loader.LoaderDetectionResult;
import dk.mcmsm.services.loader.LoaderService;
import dk.mcmsm.services.loader.LoaderType;
import dk.mcmsm.util.ZipArchiveUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Handles modpack file operations: archive extraction, directory management,
 * server configuration sync, and file copy/delete operations.
 */
@Service
public class ModPackFileService {
    private static final Logger logger = LoggerFactory.getLogger(ModPackFileService.class);
    private static final PackStatus DEFAULT_STATUS = PackStatus.NOT_DEPLOYED;
    private static final String DEFAULT_PACK_VERSION = "unknown";
    private static final String DEFAULT_MINECRAFT_VERSION = "unknown";
    private static final Integer DEFAULT_JAVA_VERSION = 21;
    public static final String DEFAULT_JAVA_XMX = "5G";
    private static final String DEFAULT_PORT = "25565";
    private static final String DEFAULT_ENTRYPOINT = "startserver.sh";
    private static final String MCMSM_LAUNCH_SCRIPT = "mcmsm-launch.sh";
    private static final String[] DEFAULT_ENTRYPOINT_CANDIDATES = new String[]{DEFAULT_ENTRYPOINT};
    private static final int DEFAULT_XMX_MIB = 8192;
    private static final int XMX_OVERHEAD_MIB = 250;
    private static final Pattern XMX_PATTERN = Pattern.compile("(?i)^(?:-Xmx)?(\\d+)([mMgG])$");
    private static final Pattern PACK_VERSION_PATTERN = Pattern.compile("(?:^|[-_])(\\d+(?:[.-]\\d+){2,})");

    private final Path modpacksRoot;
    private final Path tempRoot;
    private final ResourceLoader resourceLoader;
    private final LoaderService loaderService;

    public ModPackFileService(
            @Value("${app.storage.modpacks-root:modpacks}") String modpacksRootPath,
            @Value("${app.storage.temp-root:${java.io.tmpdir}/application-demo}") String tempRootPath,
            ResourceLoader resourceLoader,
            LoaderService loaderService
    ) {
        this.modpacksRoot = Path.of(modpacksRootPath).toAbsolutePath().normalize();
        this.tempRoot = Path.of(tempRootPath).toAbsolutePath().normalize();
        this.resourceLoader = resourceLoader;
        this.loaderService = loaderService;
    }

    /**
     * Creates a draft modpack from an uploaded archive file.
     * Extracts the archive, copies template files, and infers metadata.
     *
     * @param file the uploaded modpack archive
     * @return a new unsaved {@link ModPack} with inferred metadata
     */
    public ModPack createDraftModPackFromFile(MultipartFile file) {
        Objects.requireNonNull(file, "file must not be null");

        var originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown-modpack.zip");
        var normalizedName = originalName.replace('\\', '/');
        var fileName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
        var packVersion = inferPackVersion(fileName);
        var inferredName = sanitizePackName(stripExtension(fileName), packVersion);
        var stagingDirectory = modpacksRoot.resolve("draft-" + UUID.randomUUID()).normalize();
        var archivePath = tempRoot.resolve(UUID.randomUUID() + "-" + fileName).normalize();

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
                logger.warn("Failed to clean temporary archive '{}'.", archivePath);
            }
        }

        // Detect loader, run/download installer if needed, generate mcmsm-launch.sh
        var loaderResult = prepareLoader(stagingDirectory, inferredName);

        // Re-scan entry points after installer may have created new scripts
        var entryPointCandidates = resolveEntryPointCandidatesWithLaunchScript(stagingDirectory);
        var entryPoint = MCMSM_LAUNCH_SCRIPT;
        var javaXmx = resolveJavaXmx(stagingDirectory.resolve("user_jvm_args.txt"));
        var detectedMcVersion = loaderResult.minecraftVersion() != null
                ? loaderResult.minecraftVersion()
                : DEFAULT_MINECRAFT_VERSION;
        var loaderType = loaderResult.loaderType().wireFormat();
        var now = Instant.now();

        return new ModPack(
                inferredName,
                stagingDirectory.toString(),
                packVersion,
                detectedMcVersion,
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
                now,
                loaderType,
                loaderResult.warnings()
        );
    }

    /**
     * Synchronizes the server-port entry in server.properties with the modpack's configured port.
     *
     * @param modPack the modpack whose port should be written
     */
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

    /**
     * Calculates the Docker container memory limit from the modpack's JVM Xmx setting.
     *
     * @param modPack the modpack to resolve memory for
     * @return memory limit in MiB (Xmx + overhead)
     */
    public int resolveContainerMemoryLimitMiB(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        var xmxMiB = parseXmxMiB(Objects.requireNonNullElse(modPack.getJavaXmx(), DEFAULT_JAVA_XMX)).orElse(DEFAULT_XMX_MIB);
        var limitMiB = xmxMiB + XMX_OVERHEAD_MIB;
        logger.debug("Resolved memory limit for packId={}: xmxMiB={}, overheadMiB={}, limitMiB={}", modPack.getPackId(), xmxMiB, XMX_OVERHEAD_MIB, limitMiB);
        return limitMiB;
    }

    /**
     * Checks whether the modpack's directory exists on disk.
     *
     * @param modPack the modpack to check
     * @return true if the directory exists
     */
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

    /**
     * Recursively deletes the modpack's directory from disk.
     *
     * @param modPack the modpack whose directory should be deleted
     */
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

    /**
     * Moves the modpack directory from its staging path to a permanent path based on pack ID.
     *
     * @param modPack the modpack to relocate (must have a non-null pack ID)
     */
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

    /**
     * Copies a file or directory from one modpack root to another, overwriting existing content.
     *
     * @param item the relative path of the item to copy
     * @param from the source root directory
     * @param to   the target root directory
     */
    public void copyAndOverwriteFileFromTo(String item, String from, String to) {
        if (!hasText(item) || !hasText(from) || !hasText(to)) {
            throw new IllegalArgumentException("item, from and to must contain text.");
        }

        var sourceRoot = Path.of(from).toAbsolutePath().normalize();
        var targetRoot = Path.of(to).toAbsolutePath().normalize();
        var sourcePath = sourceRoot.resolve(item).normalize();
        var targetPath = targetRoot.resolve(item).normalize();

        ensurePathWithinRoot(sourcePath, sourceRoot, "source");
        ensurePathWithinRoot(targetPath, targetRoot, "target");

        if (!Files.exists(sourcePath)) {
            throw new IllegalStateException("Source path does not exist: " + sourcePath);
        }

        try {
            Files.createDirectories(targetRoot);
            copyRecursivelyWithOverwrite(sourcePath, targetPath);
            logger.info("Copied '{}' from '{}' to '{}' with overwrite enabled.", item, sourceRoot, targetRoot);
        } catch (IOException e) {
            logger.error("Failed copying '{}' from '{}' to '{}'.", item, sourceRoot, targetRoot, e);
            throw new IllegalStateException("Failed copying item from source to target.", e);
        }
    }

    /**
     * Copies a file or directory from one modpack root to another if the source exists.
     * Skips silently with a debug log when the source item is not present.
     *
     * @param item the relative path of the item to copy
     * @param from the source root directory
     * @param to   the target root directory
     * @return {@code true} if the item existed and was copied, {@code false} if skipped
     */
    public boolean copyIfExists(String item, String from, String to) {
        if (!hasText(item) || !hasText(from) || !hasText(to)) {
            throw new IllegalArgumentException("item, from and to must contain text.");
        }

        var sourceRoot = Path.of(from).toAbsolutePath().normalize();
        var sourcePath = sourceRoot.resolve(item).normalize();
        ensurePathWithinRoot(sourcePath, sourceRoot, "source");

        if (!Files.exists(sourcePath)) {
            logger.debug("Skipping copy of '{}' from '{}' — source does not exist.", item, sourceRoot);
            return false;
        }

        copyAndOverwriteFileFromTo(item, from, to);
        return true;
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

    private LoaderDetectionResult prepareLoader(Path stagingDirectory, String packName) {
        try {
            var result = loaderService.prepareModpack(stagingDirectory);
            result.warnings().forEach(warning ->
                    logger.warn("Loader warning for '{}': {}", packName, warning));
            return result;
        } catch (Exception e) {
            logger.error("Loader preparation failed for '{}'. Falling back to defaults.", packName, e);
            return LoaderDetectionResult.unknownWithWarning(
                    "Loader preparation failed: " + e.getMessage());
        }
    }

    /**
     * Resolves entry point candidates ensuring mcmsm-launch.sh is first.
     * Scans after installer execution so newly created scripts are included.
     */
    private String[] resolveEntryPointCandidatesWithLaunchScript(Path packDirectory) {
        var baseCandidates = resolveEntryPointCandidates(packDirectory);
        // Ensure mcmsm-launch.sh is first, followed by all others (deduplicated)
        var allCandidates = new java.util.ArrayList<String>();
        allCandidates.add(MCMSM_LAUNCH_SCRIPT);
        Arrays.stream(baseCandidates)
                .filter(name -> !name.equals(MCMSM_LAUNCH_SCRIPT))
                .forEach(allCandidates::add);
        return allCandidates.toArray(String[]::new);
    }

    private void ensureJvmArgsExists(Path packDirectory) throws IOException {
        var jvmArgsPath = packDirectory.resolve("user_jvm_args.txt");
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
        for (var line : lines) {
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
        var noPackVersion = name.replace(packVersion, " ");
        var cleaned = noPackVersion.replaceAll("[^a-zA-Z]", " ");
        var collapsed = cleaned.replaceAll("\\s{2,}", " ");
        return collapsed.isBlank() ? "modpack" : collapsed.trim();
    }

    private String inferPackVersion(String fileName) {
        if (!hasText(fileName)) {
            return DEFAULT_PACK_VERSION;
        }

        var matcher = PACK_VERSION_PATTERN.matcher(fileName);
        if (!matcher.find()) {
            return DEFAULT_PACK_VERSION;
        }

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

    private void copyRecursivelyWithOverwrite(Path sourcePath, Path targetPath) throws IOException {
        var sourceIsDirectory = Files.isDirectory(sourcePath);
        if (!sourceIsDirectory) {
            deletePathIfTypeConflicts(targetPath, false);
            Files.createDirectories(Objects.requireNonNullElse(targetPath.getParent(), targetPath));
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }

        deletePathIfTypeConflicts(targetPath, true);
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                var relative = sourcePath.relativize(dir);
                var destination = targetPath.resolve(relative);
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                var relative = sourcePath.relativize(file);
                var destination = targetPath.resolve(relative);
                Files.createDirectories(Objects.requireNonNullElse(destination.getParent(), targetPath));
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deletePathIfTypeConflicts(Path candidate, boolean expectedDirectory) throws IOException {
        if (!Files.exists(candidate)) {
            return;
        }

        var isDirectory = Files.isDirectory(candidate);
        if (isDirectory == expectedDirectory) {
            return;
        }

        deleteRecursively(candidate);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed deleting path: " + current, e);
                        }
                    });
        }
    }

    private void ensurePathWithinRoot(Path candidate, Path root, String label) {
        if (candidate.startsWith(root)) {
            return;
        }

        throw new IllegalArgumentException("Resolved " + label + " path escaped its root: " + candidate);
    }
}
