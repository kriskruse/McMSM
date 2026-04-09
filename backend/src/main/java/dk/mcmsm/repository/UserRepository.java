package dk.mcmsm.repository;

import dk.mcmsm.entities.UserEntity;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    List<UserEntity> findAll();
    UserEntity save(UserEntity userEntity);
    boolean existsByUsername(String username);
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByUsernameAndPassword(String username, String password);
}
