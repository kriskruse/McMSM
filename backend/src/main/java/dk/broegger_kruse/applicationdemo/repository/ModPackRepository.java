package dk.broegger_kruse.applicationdemo.repository;

import dk.broegger_kruse.applicationdemo.entities.ModPack;

import java.util.List;
import java.util.Optional;

public interface ModPackRepository {
    List<ModPack> findAll();
    List<ModPack> getAllByIsDeployed(Boolean isDeployed);
    ModPack save(ModPack modPack);
    Boolean existsByName(String name);
    Optional<ModPack> findByPackId(Long packId);
    void delete(ModPack modPack);
    void deleteAll(List<ModPack> modPacks);
    void deleteByPackId(Long packId);

}
