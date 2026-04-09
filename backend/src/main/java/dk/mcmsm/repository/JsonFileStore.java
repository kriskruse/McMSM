package dk.mcmsm.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import dk.mcmsm.entities.PackStatus;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Generic JSON-file-backed store with read/write locking and atomic writes.
 *
 * @param <T> the entity type stored in the JSON array
 */
public class JsonFileStore<T> {

    private final Path filePath;
    private final Path metadataRoot;
    private final Gson gson;
    private final Type listType;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a new JSON file store.
     *
     * @param filePath     path to the JSON file
     * @param metadataRoot parent directory for temp files during atomic writes
     * @param listType     Gson type token for {@code List<T>}
     */
    public JsonFileStore(Path filePath, Path metadataRoot, Type listType) {
        this.filePath = filePath;
        this.metadataRoot = metadataRoot;
        this.listType = listType;
        this.gson = buildGson();
        initialize();
    }

    /**
     * Reads all entries under a read lock.
     *
     * @return mutable list of all stored entries
     */
    public List<T> readAll() {
        var readLock = lock.readLock();
        readLock.lock();
        try {
            return readJsonList();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Executes a write operation under an exclusive write lock.
     * The mutation receives a mutable list of all entries and should modify it in place.
     * The modified list is written back atomically.
     *
     * @param mutation operation that modifies the entry list
     */
    public void write(java.util.function.Consumer<List<T>> mutation) {
        var writeLock = lock.writeLock();
        writeLock.lock();
        try {
            List<T> entries = readJsonList();
            mutation.accept(entries);
            writeJsonList(entries);
        } finally {
            writeLock.unlock();
        }
    }

    private void initialize() {
        try {
            Files.createDirectories(metadataRoot);
            if (!Files.exists(filePath)) {
                Files.createDirectories(Objects.requireNonNullElse(filePath.getParent(), metadataRoot));
                Files.writeString(filePath, "[]", CREATE, TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed initializing JSON file store: " + filePath, e);
        }
    }

    private List<T> readJsonList() {
        try {
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

    private void writeJsonList(List<T> entries) {
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

    private static Gson buildGson() {
        JsonSerializer<Instant> instantSerializer = (src, typeOfSrc, context) -> new JsonPrimitive(src.toString());
        JsonDeserializer<Instant> instantDeserializer = (json, typeOfT, context) -> {
            try {
                return Instant.parse(json.getAsString());
            } catch (Exception ex) {
                throw new JsonParseException("Invalid Instant value: " + json, ex);
            }
        };
        JsonSerializer<PackStatus> packStatusSerializer = (src, typeOfSrc, context) -> new JsonPrimitive(src.getValue());
        JsonDeserializer<PackStatus> packStatusDeserializer = (json, typeOfT, context) -> PackStatus.fromValue(json.getAsString());

        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, instantSerializer)
                .registerTypeAdapter(Instant.class, instantDeserializer)
                .registerTypeAdapter(PackStatus.class, packStatusSerializer)
                .registerTypeAdapter(PackStatus.class, packStatusDeserializer)
                .create();
    }
}
