package dk.mcmsm.services.loader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of detecting a mod loader from an extracted modpack directory.
 *
 * @param loaderType      the detected loader type
 * @param loaderVersion   the detected loader version (e.g., "47.3.0" for Forge), or null if unknown
 * @param minecraftVersion the detected Minecraft version (e.g., "1.20.1"), or null if not determined
 * @param installerJar    path to an existing installer JAR in the pack, or null if none found
 * @param serverJar       path to the main server JAR, or null if none found
 * @param hasLibraries    whether a libraries/ directory exists (signals already-installed modern loader)
 * @param isLegacyForge   true if Forge pre-1.17 (universal JAR, no @argfile support in Java 8)
 * @param warnings        list of non-fatal warning messages surfaced to the user
 */
public record LoaderDetectionResult(
        LoaderType loaderType,
        String loaderVersion,
        String minecraftVersion,
        Path installerJar,
        Path serverJar,
        boolean hasLibraries,
        boolean isLegacyForge,
        List<String> warnings
) {

    /**
     * Creates a default result for an unknown loader with no detection data.
     *
     * @return a result with UNKNOWN type and empty fields
     */
    public static LoaderDetectionResult unknown() {
        return new LoaderDetectionResult(
                LoaderType.UNKNOWN, null, null, null, null, false, false, List.of()
        );
    }

    /**
     * Creates an unknown result with a single warning message.
     *
     * @param warning the warning message
     * @return a result with UNKNOWN type and the given warning
     */
    public static LoaderDetectionResult unknownWithWarning(String warning) {
        return new LoaderDetectionResult(
                LoaderType.UNKNOWN, null, null, null, null, false, false, List.of(warning)
        );
    }

    /**
     * Mutable builder for constructing a LoaderDetectionResult incrementally during detection.
     */
    public static final class Builder {
        private LoaderType loaderType = LoaderType.UNKNOWN;
        private String loaderVersion;
        private String minecraftVersion;
        private Path installerJar;
        private Path serverJar;
        private boolean hasLibraries;
        private boolean isLegacyForge;
        private final List<String> warnings = new ArrayList<>();

        public Builder loaderType(LoaderType loaderType) {
            this.loaderType = loaderType;
            return this;
        }

        public Builder loaderVersion(String loaderVersion) {
            this.loaderVersion = loaderVersion;
            return this;
        }

        public Builder minecraftVersion(String minecraftVersion) {
            this.minecraftVersion = minecraftVersion;
            return this;
        }

        public Builder installerJar(Path installerJar) {
            this.installerJar = installerJar;
            return this;
        }

        public Builder serverJar(Path serverJar) {
            this.serverJar = serverJar;
            return this;
        }

        public Builder hasLibraries(boolean hasLibraries) {
            this.hasLibraries = hasLibraries;
            return this;
        }

        public Builder isLegacyForge(boolean isLegacyForge) {
            this.isLegacyForge = isLegacyForge;
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public LoaderDetectionResult build() {
            return new LoaderDetectionResult(
                    loaderType, loaderVersion, minecraftVersion,
                    installerJar, serverJar, hasLibraries, isLegacyForge,
                    List.copyOf(warnings)
            );
        }
    }
}
