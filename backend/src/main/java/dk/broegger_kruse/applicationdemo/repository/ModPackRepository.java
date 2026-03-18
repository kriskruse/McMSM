package dk.broegger_kruse.applicationdemo.repository;

import dk.broegger_kruse.applicationdemo.entities.ModPack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModPackRepository extends JpaRepository<ModPack, Long> {
    List<ModPack> getAllByIsDeployed(Boolean isDeployed);
    Boolean existsByName(String name);
    Optional<ModPack> findByPackId(Long packId);
    void deleteByPackId(Long packId);

}
