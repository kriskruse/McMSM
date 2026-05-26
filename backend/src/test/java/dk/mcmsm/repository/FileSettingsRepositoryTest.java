package dk.mcmsm.repository;

import dk.mcmsm.entities.AppSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSettingsRepositoryTest {

    @Test
    void loadReturnsEmptyWhenFileMissing(@TempDir Path tempDir) {
        var repo = new FileSettingsRepository(tempDir.toString());
        assertNull(repo.load().curseforgeApiKey());
    }

    @Test
    void saveAndLoadRoundtrips(@TempDir Path tempDir) {
        var repo = new FileSettingsRepository(tempDir.toString());
        repo.save(new AppSettings("encrypted-cf-token"));

        var loaded = repo.load();

        assertEquals("encrypted-cf-token", loaded.curseforgeApiKey());
        assertTrue(Files.exists(tempDir.resolve("settings.json")));
    }

    @Test
    void saveOverwritesPriorValue(@TempDir Path tempDir) {
        var repo = new FileSettingsRepository(tempDir.toString());
        repo.save(new AppSettings("first"));
        repo.save(new AppSettings("second"));

        assertEquals("second", repo.load().curseforgeApiKey());
    }
}
