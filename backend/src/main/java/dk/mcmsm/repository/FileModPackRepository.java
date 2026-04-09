package dk.mcmsm.repository;

import com.google.gson.reflect.TypeToken;
import dk.mcmsm.entities.ModPack;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * File-backed implementation of {@link ModPackRepository}.
 * Stores modpack metadata as a JSON array and delegates read/write locking
 * to {@link JsonFileStore}.
 */
@Repository
public class FileModPackRepository implements ModPackRepository {

    private final JsonFileStore<ModPack> store;

    /**
     * Creates the repository and initializes the backing JSON file.
     *
     * @param metadataRootPath directory containing the JSON metadata files
     */
    public FileModPackRepository(
            @Value("${app.storage.metadata-root:data}") String metadataRootPath
    ) {
        var metadataRoot = Path.of(metadataRootPath).toAbsolutePath().normalize();
        var filePath = metadataRoot.resolve("modpacks.json");
        this.store = new JsonFileStore<>(filePath, metadataRoot, new TypeToken<List<ModPack>>() {}.getType());
    }

    @Override
    public List<ModPack> findAll() {
        return store.readAll();
    }

    @Override
    public List<ModPack> getAllByIsDeployed(Boolean isDeployed) {
        var deployed = Boolean.TRUE.equals(isDeployed);
        return store.readAll().stream()
                .filter(pack -> Objects.equals(Boolean.TRUE.equals(pack.getIsDeployed()), deployed))
                .toList();
    }

    @Override
    public ModPack save(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");

        store.write(modPacks -> {
            if (modPack.getPackId() == null) {
                modPack.setPackId(nextId(modPacks));
                modPacks.add(modPack);
            } else {
                var replaced = false;
                for (var i = 0; i < modPacks.size(); i++) {
                    if (Objects.equals(modPacks.get(i).getPackId(), modPack.getPackId())) {
                        modPacks.set(i, modPack);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    modPacks.add(modPack);
                }
            }
        });

        return modPack;
    }

    @Override
    public Boolean existsByName(String name) {
        return store.readAll().stream().anyMatch(pack -> Objects.equals(pack.getName(), name));
    }

    @Override
    public Optional<ModPack> findByPackId(Long packId) {
        return store.readAll().stream()
                .filter(pack -> Objects.equals(pack.getPackId(), packId))
                .findFirst();
    }

    @Override
    public void delete(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        deleteByPackId(modPack.getPackId());
    }

    @Override
    public void deleteAll(List<ModPack> modPacks) {
        var idsToDelete = modPacks.stream().map(ModPack::getPackId).toList();
        store.write(entries -> entries.removeIf(pack -> idsToDelete.contains(pack.getPackId())));
    }

    @Override
    public void deleteByPackId(Long packId) {
        store.write(entries -> entries.removeIf(pack -> Objects.equals(pack.getPackId(), packId)));
    }

    private Long nextId(List<ModPack> modPacks) {
        return modPacks.stream()
                .map(ModPack::getPackId)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;
    }
}
