package dk.mcmsm.services;

import dk.mcmsm.dto.responses.ConfigFileDto;
import dk.mcmsm.entities.ModPack;
import dk.mcmsm.util.PathSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads and writes JSON config files stored under a modpack's {@code config/} directory.
 *
 * <p>This service treats config files as opaque text: it never parses JSON. All
 * JSON(C) parsing, comment handling, and re-serialization are the frontend's
 * responsibility. The service's job is to safely enumerate and transfer raw file
 * bytes while preventing path-traversal escapes outside the {@code config/} root.
 */
@Service
public class ModPackConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ModPackConfigService.class);

    private static final String CONFIG_DIRECTORY_NAME = "config";
    private static final String JSON_SUFFIX = ".json";
    private static final String TOML_SUFFIX = ".toml";
    private static final String TEMP_FILE_PREFIX = ".mcmsm-config-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    /**
     * Lists every {@code .json} file under the pack's {@code config/} directory, recursively.
     *
     * @param modPack the modpack whose config files are listed.
     * @return config file descriptors with paths relative to {@code config/}; empty if no {@code config/} dir exists.
     */
    public List<ConfigFileDto> listConfigFiles(ModPack modPack) {
        var configRoot = resolveConfigRoot(modPack);
        if (!Files.isDirectory(configRoot)) {
            logger.debug("No config directory for modpack packId={} at {}", modPack.getPackId(), configRoot);
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(configRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(ModPackConfigService::isValidConfigFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> toConfigFileDto(configRoot, path))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed listing config files for modpack packId=" + modPack.getPackId(), e);
        }
    }

    /**
     * Reads a single config file as UTF-8 text.
     *
     * @param modPack     the owning modpack.
     * @param relativePath path relative to the pack's {@code config/} root.
     * @return the file contents as a string.
     * @throws IllegalArgumentException if the path escapes the config root or is not a {@code .json} file.
     */
    public String readConfigFile(ModPack modPack, String relativePath) {
        var target = resolveAndValidate(modPack, relativePath);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Config file not found: " + relativePath);
        }
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading config file: " + relativePath, e);
        }
    }

    /**
     * Writes a single config file as UTF-8 text, atomically replacing any existing file.
     *
     * <p>Content is written to a temporary file in the same directory and then moved into
     * place so a crash mid-write cannot corrupt a live config.
     *
     * @param modPack      the owning modpack.
     * @param relativePath path relative to the pack's {@code config/} root.
     * @param content      the new file contents.
     * @throws IllegalArgumentException if the path escapes the config root or is not a {@code .json} file.
     */
    public void writeConfigFile(ModPack modPack, String relativePath, String content) {
        var target = resolveAndValidate(modPack, relativePath);
        var parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("Config file directory does not exist: " + relativePath);
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(parent, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            moveIntoPlace(tempFile, target);
            tempFile = null;
            logger.info("Wrote config file '{}' for modpack packId={}", relativePath, modPack.getPackId());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed writing config file: " + relativePath, e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException atomicUnsupported) {
            logger.debug("Atomic move unsupported for {}, falling back to replace move", target);
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Failed deleting temporary config file {}", path, e);
        }
    }

    private Path resolveConfigRoot(ModPack modPack) {
        var packPath = Path.of(modPack.getPath()).toAbsolutePath().normalize();
        return packPath.resolve(CONFIG_DIRECTORY_NAME).normalize();
    }

    private Path resolveAndValidate(ModPack modPack, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Config file path must not be blank.");
        }
        var configRoot = resolveConfigRoot(modPack);
        var target = configRoot.resolve(relativePath).normalize();
        PathSafety.ensureWithinRoot(target, configRoot, "config file");
        if (!isValidConfigFile(target)) {
            throw new IllegalArgumentException("Only .json or .toml config files may be accessed: " + relativePath);
        }
        return target;
    }

    private ConfigFileDto toConfigFileDto(Path configRoot, Path file) {
        var relativePath = configRoot.relativize(file).toString().replace('\\', '/');
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            logger.warn("Failed reading size of config file {}", file, e);
            size = 0L;
        }
        return new ConfigFileDto(relativePath, file.getFileName().toString(), size);
    }

    private static boolean isValidConfigFile(Path path) {
        return isJsonFile(path) || isTomlFile(path);
    }

    private static boolean isJsonFile(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(JSON_SUFFIX);
    }

    private static boolean isTomlFile(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(TOML_SUFFIX);
    }
}
