package dk.mcmsm.dto.requests;

/**
 * Request body for sending a command to a running modpack container.
 *
 * @param command the Minecraft console command to execute.
 */
public record CommandRequestDto(String command) {
}
