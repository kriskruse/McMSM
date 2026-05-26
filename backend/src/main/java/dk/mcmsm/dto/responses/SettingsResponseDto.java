package dk.mcmsm.dto.responses;

/**
 * Response payload returned by the settings endpoints. Secret values are never
 * sent back to clients; only a boolean indicating whether the secret is
 * configured is exposed.
 *
 * @param curseforgeApiKeyConfigured whether a CurseForge API key is currently
 *                                   stored
 */
public record SettingsResponseDto(boolean curseforgeApiKeyConfigured) {
}
