package dk.mcmsm.services;

import dk.mcmsm.entities.AppSettings;
import dk.mcmsm.repository.SettingsRepository;
import dk.mcmsm.util.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Coordinates encryption and persistence of application settings. The repository
 * stores values as written; this service is the single boundary that encrypts
 * secret fields on save and decrypts them on read.
 */
@Service
public class SettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsRepository repository;
    private final SecretCipher cipher;

    /**
     * Creates the service.
     *
     * @param repository persistence backend
     * @param cipher     encryption helper
     */
    public SettingsService(SettingsRepository repository, SecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /**
     * Returns the CurseForge API key in plaintext, if configured.
     *
     * @return optional decrypted CurseForge API key
     */
    public Optional<String> getCurseforgeApiKey() {
        var stored = repository.load().curseforgeApiKey();
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(cipher.decrypt(stored));
    }

    /**
     * Returns whether a CurseForge API key is currently stored.
     *
     * @return {@code true} if a key is configured
     */
    public boolean isCurseforgeApiKeyConfigured() {
        var stored = repository.load().curseforgeApiKey();
        return stored != null && !stored.isBlank();
    }

    /**
     * Applies a partial update to the settings. A {@code null} field on the
     * incoming patch leaves the corresponding stored value unchanged; an empty
     * string clears it.
     *
     * @param curseforgeApiKey new CurseForge API key, {@code null} to leave
     *                         unchanged, or empty to clear
     */
    public void updateCurseforgeApiKey(String curseforgeApiKey) {
        if (curseforgeApiKey == null) {
            return;
        }
        String nextValue;
        if (curseforgeApiKey.isEmpty()) {
            nextValue = null;
            logger.info("Cleared CurseForge API key.");
        } else {
            nextValue = cipher.encrypt(curseforgeApiKey);
            logger.info("Stored new CurseForge API key.");
        }
        repository.save(new AppSettings(nextValue));
    }
}
