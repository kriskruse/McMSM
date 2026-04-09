package dk.mcmsm.repository;

import com.google.gson.reflect.TypeToken;
import dk.mcmsm.entities.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * File-backed implementation of {@link UserRepository}.
 * Stores user data as a JSON array and delegates read/write locking
 * to {@link JsonFileStore}.
 */
@Repository
public class FileUserRepository implements UserRepository {

    private final JsonFileStore<UserEntity> store;

    /**
     * Creates the repository and initializes the backing JSON file.
     *
     * @param metadataRootPath directory containing the JSON metadata files
     */
    public FileUserRepository(
            @Value("${app.storage.metadata-root:data}") String metadataRootPath
    ) {
        var metadataRoot = Path.of(metadataRootPath).toAbsolutePath().normalize();
        var filePath = metadataRoot.resolve("users.json");
        this.store = new JsonFileStore<>(filePath, metadataRoot, new TypeToken<List<UserEntity>>() {}.getType());
    }

    @Override
    public List<UserEntity> findAll() {
        return store.readAll();
    }

    @Override
    public UserEntity save(UserEntity userEntity) {
        Objects.requireNonNull(userEntity, "userEntity must not be null");

        store.write(users -> {
            if (userEntity.getId() == null) {
                userEntity.setId(nextId(users));
                users.add(userEntity);
            } else {
                var replaced = false;
                for (var i = 0; i < users.size(); i++) {
                    if (Objects.equals(users.get(i).getId(), userEntity.getId())) {
                        users.set(i, userEntity);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    users.add(userEntity);
                }
            }
        });

        return userEntity;
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public Optional<UserEntity> findByUsername(String username) {
        return store.readAll().stream()
                .filter(user -> Objects.equals(user.getUsername(), username))
                .findFirst();
    }

    @Override
    public Optional<UserEntity> findByUsernameAndPassword(String username, String password) {
        return store.readAll().stream()
                .filter(user -> Objects.equals(user.getUsername(), username) && Objects.equals(user.getPassword(), password))
                .findFirst();
    }

    private Long nextId(List<UserEntity> users) {
        return users.stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;
    }
}
