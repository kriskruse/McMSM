package dk.mcmsm.exception;

/**
 * Thrown when a modpack lifecycle operation (deploy, start, stop, etc.) fails.
 */
public class ModPackOperationException extends RuntimeException {

    private final Long packId;
    private final String operation;

    public ModPackOperationException(Long packId, String operation, Throwable cause) {
        super("Failed to " + operation + " mod pack with ID: " + packId, cause);
        this.packId = packId;
        this.operation = operation;
    }

    /**
     * Returns the ID of the modpack where the operation failed.
     *
     * @return the pack ID
     */
    public Long getPackId() {
        return packId;
    }

    /**
     * Returns the name of the failed operation (e.g. "deploy", "start").
     *
     * @return the operation name
     */
    public String getOperation() {
        return operation;
    }
}
