package dk.mcmsm.repository;

import dk.mcmsm.entities.ModPack;
import dk.mcmsm.services.FileService;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FileModPackRepository implements ModPackRepository {

    private final FileService fileService;

    public FileModPackRepository(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public List<ModPack> findAll() {
        return fileService.getAllModPacks();
    }

    @Override
    public List<ModPack> getAllByIsDeployed(Boolean isDeployed) {
        return fileService.getAllModPacksByDeployment(Boolean.TRUE.equals(isDeployed));
    }

    @Override
    public ModPack save(ModPack modPack) {
        return fileService.saveModPack(modPack);
    }

    @Override
    public Boolean existsByName(String name) {
        return fileService.existsModPackByName(name);
    }

    @Override
    public Optional<ModPack> findByPackId(Long packId) {
        return fileService.findModPackById(packId);
    }

    @Override
    public void delete(ModPack modPack) {
        fileService.deleteModPack(modPack);
    }

    @Override
    public void deleteAll(List<ModPack> modPacks) {
        fileService.deleteAllModPacks(modPacks);
    }

    @Override
    public void deleteByPackId(Long packId) {
        fileService.deleteModPackById(packId);
    }
}

