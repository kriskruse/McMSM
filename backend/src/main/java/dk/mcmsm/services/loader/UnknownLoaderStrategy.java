package dk.mcmsm.services.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Fallback strategy for unrecognized mod loaders.
 * Generates a delegation script that runs the first available .sh file.
 */
public final class UnknownLoaderStrategy implements LoaderStrategy {
    private static final Logger logger = LoggerFactory.getLogger(UnknownLoaderStrategy.class);

    @Override
    public LoaderType loaderType() {
        return LoaderType.UNKNOWN;
    }

    /**
     * Always returns false — the unknown strategy does not detect anything.
     */
    @Override
    public boolean detect(Path packDir, LoaderDetectionResult.Builder builder) {
        return false;
    }

    @Override
    public List<String> installerArguments(Path installerJar, Path packDir) {
        return List.of();
    }

    @Override
    public Optional<String> resolveLatestVersion(String mcVersion, HttpClient httpClient) {
        return Optional.empty();
    }

    @Override
    public Optional<URI> installerDownloadUri(String mcVersion, String loaderVersion) {
        return Optional.empty();
    }

    @Override
    public String generateLaunchScript(Path packDir, LoaderDetectionResult detection) {
        var firstSh = findFirstShScript(packDir);
        if (firstSh != null) {
            return """
                    #!/usr/bin/env bash
                    set -eu
                    cd "$(dirname "$0")"
                    # Loader not detected. Delegating to first available script.
                    exec bash ./%s "$@"
                    """.formatted(firstSh);
        }

        // No .sh files at all — look for a server JAR
        var serverJar = findServerJar(packDir);
        if (serverJar != null) {
            return """
                    #!/usr/bin/env bash
                    set -eu
                    cd "$(dirname "$0")"
                    JVM_ARGS=$(grep -v '^\\s*#' user_jvm_args.txt | grep -v '^\\s*$' | tr '\\n' ' ')
                    exec java $JVM_ARGS -jar %s nogui "$@"
                    """.formatted(serverJar);
        }

        return """
                #!/usr/bin/env bash
                set -eu
                cd "$(dirname "$0")"
                echo "[McMSM] ERROR: No mod loader detected and no launch script found."
                echo "[McMSM] Please select a different entry point in the metadata settings,"
                echo "[McMSM] or re-upload the modpack with an installer or start script included."
                exit 1
                """;
    }

    private String findFirstShScript(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".sh") && !name.equals("mcmsm-launch.sh"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private String findServerJar(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".jar") && !name.contains("installer"))
                    .filter(name -> name.contains("server") || name.contains("forge") || name.contains("neoforge"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
