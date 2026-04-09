package dk.mcmsm.repository;

import dk.mcmsm.entities.UserEntity;
import dk.mcmsm.services.FileService;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FileUserRepository implements UserRepository {

    private final FileService fileService;

    public FileUserRepository(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public List<UserEntity> findAll() {
        return fileService.getAllUsers();
    }

    @Override
    public UserEntity save(UserEntity userEntity) {
        return fileService.saveUser(userEntity);
    }

    @Override
    public boolean existsByUsername(String username) {
        return fileService.existsByUsername(username);
    }

    @Override
    public Optional<UserEntity> findByUsername(String username) {
        return fileService.findUserByUsername(username);
    }

    @Override
    public Optional<UserEntity> findByUsernameAndPassword(String username, String password) {
        return fileService.findUserByUsernameAndPassword(username, password);
    }
}

