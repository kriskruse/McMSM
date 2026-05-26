package dk.mcmsm.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dk.mcmsm.entities.AppSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * File-backed implementation of {@link SettingsRepository}.
 * Persists a single JSON object to {@code data/settings.json} with atomic writes
 * and read/write locking.
 */
@Repository
public class FileSettingsRepository implements SettingsRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;
    private final Path metadataRoot;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates the repository and ensures the metadata directory exists.
     *
     * @param metadataRootPath directory containing the JSON metadata files
     */
    public FileSettingsRepository(@Value("${app.storage.metadata-root:data}") String metadataRootPath) {
        this.metadataRoot = Path.of(metadataRootPath).toAbsolutePath().normalize();
        this.filePath = metadataRoot.resolve("settings.json");
        initialize();
    }

    @Override
    public AppSettings load() {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            if (!Files.exists(filePath)) {
                return AppSettings.empty();
            }
            var raw = Files.readString(filePath, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return AppSettings.empty();
            }
            var parsed = GSON.fromJson(raw, AppSettings.class);
            return parsed == null ? AppSettings.empty() : parsed;
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading settings file: " + filePath, e);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void save(AppSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            Files.createDirectories(Objects.requireNonNullElse(filePath.getParent(), metadataRoot));
            var tempFile = Files.createTempFile(metadataRoot, "settings-", ".tmp");
            try {
                Files.writeString(tempFile, GSON.toJson(settings), StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING);
                moveAtomicallyOrReplace(tempFile, filePath);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed writing settings file: " + filePath, e);
        } finally {
            writeLock.unlock();
        }
    }

    private void initialize() {
        try {
            Files.createDirectories(metadataRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed initializing settings store under " + metadataRoot, e);
        }
    }

    private void moveAtomicallyOrReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
