package dk.mcmsm.dto.requests;

/**
 * Request payload for updating application settings.
 * A {@code null} field leaves the corresponding setting unchanged; an empty
 * string clears it.
 *
 * @param curseforgeApiKey new CurseForge API key, or {@code null} to leave
 *                         unchanged, or empty string to clear
 */
public record SettingsUpdateRequestDto(String curseforgeApiKey) {
}
