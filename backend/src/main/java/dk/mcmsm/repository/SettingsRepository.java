package dk.mcmsm.repository;

import dk.mcmsm.entities.AppSettings;

/**
 * Persistence boundary for {@link AppSettings}. Implementations are responsible
 * only for raw read/write; encryption/decryption of secret fields happens in
 * the service layer.
 */
public interface SettingsRepository {

    /**
     * Loads the current settings, returning {@link AppSettings#empty()} if no
     * settings have been persisted yet.
     *
     * @return current settings (never {@code null})
     */
    AppSettings load();

    /**
     * Persists the given settings, overwriting any prior values.
     *
     * @param settings settings to save (must not be {@code null})
     */
    void save(AppSettings settings);
}
