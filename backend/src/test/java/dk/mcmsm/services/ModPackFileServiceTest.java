package dk.mcmsm.services;

import dk.mcmsm.entities.ModPack;
import dk.mcmsm.services.loader.LoaderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class ModPackFileServiceTest {

    private ModPackFileService newService() {
        return new ModPackFileService(
                "modpacks",
                System.getProperty("java.io.tmpdir") + "/mcmsm-test",
                mock(ResourceLoader.class),
                mock(LoaderService.class)
        );
    }

    private ModPack packAt(Path packDir) {
        var pack = new ModPack();
        pack.setPackId(42L);
        pack.setPath(packDir.toString());
        return pack;
    }

    @Test
    void deletesNestedTreeFully(@TempDir Path packDir) throws IOException {
        var deep = Files.createDirectories(packDir.resolve("generated/data/recipes"));
        Files.writeString(deep.resolve("a.json"), "{}");
        Files.writeString(packDir.resolve("top.txt"), "x");

        newService().deletePackDirectory(packAt(packDir));

        assertFalse(Files.exists(packDir));
    }

    @Test
    void deletesNothingWhenPathIsMissing(@TempDir Path tempDir) {
        var missing = tempDir.resolve("does-not-exist");
        assertDoesNotThrow(() -> newService().deletePackDirectory(packAt(missing)));
    }

    @Test
    void fixesNonWritableSubdirectoryOwnedByUsAndDeletes(@TempDir Path packDir) throws IOException {
        var locked = Files.createDirectories(packDir.resolve("generated/recipes"));
        Files.writeString(locked.resolve("recipe.json"), "{}");
        var current = Files.getPosixFilePermissions(locked);
        var stripped = Set.copyOf(current.stream()
                .filter(permission -> permission != PosixFilePermission.OWNER_WRITE)
                .toList());
        Files.setPosixFilePermissions(locked, stripped);

        newService().deletePackDirectory(packAt(packDir));

        assertFalse(Files.exists(packDir));
    }
}
