package dk.mcmsm.services.loader;

/**
 * Supported mod loader types for Minecraft servers.
 * Each type has a wire-format string used in JSON serialization.
 */
public enum LoaderType {
    FORGE("forge"),
    NEOFORGE("neoforge"),
    UNKNOWN("unknown");
    // Future: FABRIC("fabric"), QUILT("quilt")

    private final String wireFormat;

    LoaderType(String wireFormat) {
        this.wireFormat = wireFormat;
    }

    /**
     * Returns the wire-format string for JSON serialization.
     *
     * @return lowercase loader name
     */
    public String wireFormat() {
        return wireFormat;
    }

    /**
     * Parses a wire-format string back to a LoaderType.
     *
     * @param value the wire-format string (e.g., "forge", "neoforge")
     * @return the matching LoaderType, or UNKNOWN if not recognized
     */
    public static LoaderType fromWireFormat(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        for (var type : values()) {
            if (type.wireFormat.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
