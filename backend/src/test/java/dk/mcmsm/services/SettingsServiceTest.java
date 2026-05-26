package dk.mcmsm.services;

import dk.mcmsm.entities.AppSettings;
import dk.mcmsm.repository.SettingsRepository;
import dk.mcmsm.util.SecretCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsServiceTest {

    private static class InMemorySettingsRepository implements SettingsRepository {
        private final AtomicReference<AppSettings> state = new AtomicReference<>(AppSettings.empty());

        @Override
        public AppSettings load() {
            return state.get();
        }

        @Override
        public void save(AppSettings settings) {
            state.set(settings);
        }
    }

    @Test
    void updateEncryptsBeforePersisting(@TempDir Path tempDir) {
        var repo = new InMemorySettingsRepository();
        var cipher = new SecretCipher(tempDir.toString());
        var service = new SettingsService(repo, cipher);

        service.updateCurseforgeApiKey("plaintext-key");

        var stored = repo.load().curseforgeApiKey();
        assertTrue(cipher.isEncrypted(stored));
        assertNotEquals("plaintext-key", stored);
    }

    @Test
    void getCurseforgeApiKeyReturnsDecryptedValue(@TempDir Path tempDir) {
        var repo = new InMemorySettingsRepository();
        var cipher = new SecretCipher(tempDir.toString());
        var service = new SettingsService(repo, cipher);
        service.updateCurseforgeApiKey("cf-secret");

        var loaded = service.getCurseforgeApiKey();

        assertEquals(Optional.of("cf-secret"), loaded);
        assertTrue(service.isCurseforgeApiKeyConfigured());
    }

    @Test
    void emptyStringClearsTheKey(@TempDir Path tempDir) {
        var repo = new InMemorySettingsRepository();
        var cipher = new SecretCipher(tempDir.toString());
        var service = new SettingsService(repo, cipher);
        service.updateCurseforgeApiKey("temp-key");
        assertTrue(service.isCurseforgeApiKeyConfigured());

        service.updateCurseforgeApiKey("");

        assertFalse(service.isCurseforgeApiKeyConfigured());
        assertNull(repo.load().curseforgeApiKey());
        assertEquals(Optional.empty(), service.getCurseforgeApiKey());
    }

    @Test
    void nullInputLeavesSettingsUnchanged(@TempDir Path tempDir) {
        var repo = new InMemorySettingsRepository();
        var cipher = new SecretCipher(tempDir.toString());
        var service = new SettingsService(repo, cipher);
        service.updateCurseforgeApiKey("preserved-key");
        var beforeNull = repo.load().curseforgeApiKey();

        service.updateCurseforgeApiKey(null);

        assertEquals(beforeNull, repo.load().curseforgeApiKey());
    }
}
