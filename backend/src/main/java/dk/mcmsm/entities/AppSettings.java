package dk.mcmsm.entities;

/**
 * Application-wide settings persisted to {@code data/settings.json}.
 * Secret fields are encrypted at rest by {@link dk.mcmsm.util.SecretCipher}
 * before reaching this entity.
 *
 * @param curseforgeApiKey CurseForge Core API key, encrypted on disk and
 *                         decrypted in memory by the settings service.
 *                         May be {@code null} if not configured.
 */
public record AppSettings(String curseforgeApiKey) {

    /**
     * Returns an empty settings instance with all fields null.
     *
     * @return empty settings
     */
    public static AppSettings empty() {
        return new AppSettings(null);
    }
}
