package dk.mcmsm.exception;

/**
 * Thrown when a modpack with the requested ID does not exist.
 */
public class ModPackNotFoundException extends RuntimeException {

    private final Long packId;

    public ModPackNotFoundException(Long packId) {
        super("Mod pack not found with ID: " + packId);
        this.packId = packId;
    }

    /**
     * Returns the ID of the modpack that was not found.
     *
     * @return the missing pack ID
     */
    public Long getPackId() {
        return packId;
    }
}
