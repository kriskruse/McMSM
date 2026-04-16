package dk.mcmsm.exception;

import java.io.IOException;

/**
 * Thrown when a file-system operation on a modpack fails.
 * Preserves the original {@link IOException} as the cause.
 */
public class ModPackFileException extends RuntimeException {

    /**
     * Creates a new file exception with a descriptive message and the underlying I/O cause.
     *
     * @param message description of the failed operation
     * @param cause   the original I/O exception
     */
    public ModPackFileException(String message, IOException cause) {
        super(message, cause);
    }
}
