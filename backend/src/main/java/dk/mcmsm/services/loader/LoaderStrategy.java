package dk.mcmsm.services.loader;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for loader-specific behavior: detection, installer execution,
 * version resolution, installer download, and launch script generation.
 *
 * <p>Sealed to enforce exhaustive switch expressions. When Fabric/Quilt support
 * is added, the compiler will flag every switch that needs updating.</p>
 */
public sealed interface LoaderStrategy
        permits ForgeLoaderStrategy, NeoForgeLoaderStrategy, UnknownLoaderStrategy {

    /**
     * Returns the loader type this strategy handles.
     *
     * @return the loader type
     */
    LoaderType loaderType();

    /**
     * Scans the pack directory for signals of this loader type.
     * Populates the builder with any detected metadata.
     *
     * @param packDir the extracted modpack root directory
     * @param builder the detection result builder to populate
     * @return true if this loader was detected
     */
    boolean detect(Path packDir, LoaderDetectionResult.Builder builder);

    /**
     * Returns ProcessBuilder arguments to run the installer JAR.
     * The first element should be "java" (replaced with host java path by the caller).
     *
     * @param installerJar the path to the installer JAR
     * @param packDir      the pack directory (working directory for the installer)
     * @return the command arguments list
     */
    List<String> installerArguments(Path installerJar, Path packDir);

    /**
     * Resolves the latest loader version for a given Minecraft version from the internet.
     *
     * @param mcVersion  the Minecraft version (e.g., "1.20.1")
     * @param httpClient the HTTP client to use for requests
     * @return the latest loader version, or empty if resolution fails
     */
    Optional<String> resolveLatestVersion(String mcVersion, HttpClient httpClient);

    /**
     * Builds the installer download URI for the given versions.
     *
     * @param mcVersion     the Minecraft version
     * @param loaderVersion the loader version
     * @return the download URI, or empty if the URL cannot be constructed
     */
    Optional<URI> installerDownloadUri(String mcVersion, String loaderVersion);

    /**
     * Generates the content of mcmsm-launch.sh for this loader type.
     * The script should always reference user_jvm_args.txt for JVM arguments.
     *
     * @param packDir   the pack directory
     * @param detection the detection result with loader metadata
     * @return the shell script content
     */
    String generateLaunchScript(Path packDir, LoaderDetectionResult detection);
}
