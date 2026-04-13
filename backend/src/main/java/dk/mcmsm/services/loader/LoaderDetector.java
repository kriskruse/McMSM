package dk.mcmsm.services.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Orchestrates mod loader detection by running each {@link LoaderStrategy} in order.
 * Checks for manifest-only packs first (CurseForge, Modrinth, FTB) and produces
 * a warning if detected.
 */
public final class LoaderDetector {
    private static final Logger logger = LoggerFactory.getLogger(LoaderDetector.class);

    private final List<LoaderStrategy> strategies;

    /**
     * Creates a detector with the given strategies in priority order.
     *
     * @param strategies the detection strategies to try (first match wins)
     */
    public LoaderDetector(List<LoaderStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Detects the mod loader in the given pack directory.
     *
     * <p>Detection proceeds in order:
     * <ol>
     *   <li>Check for manifest-only packs (CurseForge, Modrinth). Return UNKNOWN with warning.</li>
     *   <li>Run each strategy's detect() in order. First match wins.</li>
     *   <li>If no strategy matched, return UNKNOWN.</li>
     * </ol></p>
     *
     * @param packDir the extracted modpack root directory
     * @return the detection result (never null)
     */
    public LoaderDetectionResult detect(Path packDir) {
        // Step 1: Check for manifest-only packs
        var manifestWarning = checkForManifestPack(packDir);
        if (manifestWarning != null) {
            logger.info("Manifest-only pack detected in {}: {}", packDir, manifestWarning);
            return LoaderDetectionResult.unknownWithWarning(manifestWarning);
        }

        // Step 2: Run each strategy's detect() in order
        var builder = new LoaderDetectionResult.Builder();
        for (var strategy : strategies) {
            try {
                if (strategy.detect(packDir, builder)) {
                    logger.info("Loader detected by {}: {}", strategy.getClass().getSimpleName(), strategy.loaderType());
                    return builder.build();
                }
            } catch (Exception e) {
                logger.warn("Detection error in {}: {}", strategy.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Step 3: Nothing detected
        logger.info("No mod loader detected in {}.", packDir);
        return LoaderDetectionResult.unknown();
    }

    /**
     * Checks for manifest-only pack indicators that we don't yet support.
     *
     * @param packDir the pack directory to check
     * @return a warning message if a manifest pack is detected, null otherwise
     */
    private String checkForManifestPack(Path packDir) {
        if (Files.exists(packDir.resolve("manifest.json")) && !hasJarFiles(packDir)) {
            return "This appears to be a CurseForge manifest-only pack. Mod download resolution is not yet supported. "
                    + "Please upload a server pack that includes all required files.";
        }

        if (Files.exists(packDir.resolve("modrinth.index.json")) && !hasJarFiles(packDir)) {
            return "This appears to be a Modrinth manifest-only pack. Mod download resolution is not yet supported. "
                    + "Please upload a server pack that includes all required files.";
        }

        return null;
    }

    private boolean hasJarFiles(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"));
        } catch (IOException e) {
            return false;
        }
    }
}
