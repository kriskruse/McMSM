package dk.mcmsm.dto.responses;

/**
 * Describes a single modpack config file discovered under {@code config/}.
 *
 * @param relativePath path relative to the pack's {@code config/} root, using {@code /} separators.
 * @param fileName     the file name without its directory.
 * @param sizeBytes    file size in bytes.
 */
public record ConfigFileDto(
        String relativePath,
        String fileName,
        long sizeBytes
) {
}
