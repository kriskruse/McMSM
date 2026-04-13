package dk.mcmsm.services.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Main service for modpack loader preparation: detection, installer execution,
 * installer download, and launch script generation.
 *
 * <p>Errors during any phase are captured as warnings and never prevent the
 * modpack from being saved.</p>
 */
@Service
public class LoaderService {
    private static final Logger logger = LoggerFactory.getLogger(LoaderService.class);

    private static final String MCMSM_LAUNCH_SCRIPT = "mcmsm-launch.sh";
    private static final long INSTALLER_TIMEOUT_MINUTES = 5;
    private static final String DOWNLOADED_INSTALLER_NAME = "mcmsm-downloaded-installer.jar";

    private final LoaderDetector detector;
    private final Map<LoaderType, LoaderStrategy> strategies;
    private final HttpClient httpClient;

    public LoaderService() {
        var forgeStrategy = new ForgeLoaderStrategy();
        var neoForgeStrategy = new NeoForgeLoaderStrategy();
        var unknownStrategy = new UnknownLoaderStrategy();

        this.detector = new LoaderDetector(List.of(forgeStrategy, neoForgeStrategy));
        this.strategies = Map.of(
                LoaderType.FORGE, forgeStrategy,
                LoaderType.NEOFORGE, neoForgeStrategy,
                LoaderType.UNKNOWN, unknownStrategy
        );
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Prepares a modpack by detecting its loader, running or downloading the installer
     * if needed, and generating the launch script.
     *
     * <p>This method never throws. All errors are captured as warnings in the
     * returned result.</p>
     *
     * @param packDirectory the extracted modpack root directory
     * @return the detection result with any warnings accumulated during preparation
     */
    public LoaderDetectionResult prepareModpack(Path packDirectory) {
        try {
            return doPrepare(packDirectory);
        } catch (Exception e) {
            logger.error("Unexpected error during modpack preparation in {}: {}", packDirectory, e.getMessage(), e);
            return LoaderDetectionResult.unknownWithWarning(
                    "Unexpected error during loader preparation: " + e.getMessage()
            );
        }
    }

    private LoaderDetectionResult doPrepare(Path packDirectory) {
        // Phase 1: Detection
        var detection = detector.detect(packDirectory);
        var strategy = strategies.getOrDefault(detection.loaderType(), strategies.get(LoaderType.UNKNOWN));
        var warnings = new ArrayList<>(detection.warnings());

        logger.info("Loader detection for {}: type={}, version={}, mc={}, installer={}, libraries={}",
                packDirectory.getFileName(),
                detection.loaderType(), detection.loaderVersion(),
                detection.minecraftVersion(),
                detection.installerJar() != null,
                detection.hasLibraries());

        // Phase 2: Installation
        if (detection.installerJar() != null && !detection.hasLibraries()) {
            // Installer present but not yet run — run it
            runInstaller(strategy, detection.installerJar(), packDirectory, warnings);
        } else if (!detection.hasLibraries() && detection.loaderType() != LoaderType.UNKNOWN) {
            // No installer, no libraries, but we know the loader — download and run
            downloadAndRunInstaller(strategy, detection, packDirectory, warnings);
        } else if (detection.hasLibraries()) {
            logger.info("Server already installed (libraries/ present). Skipping installer.");
        }

        // Phase 3: Launch script generation
        // Re-detect after installer may have created new files
        var postInstallDetection = redetectIfNeeded(detection, packDirectory);
        var scriptStrategy = strategies.getOrDefault(postInstallDetection.loaderType(), strategies.get(LoaderType.UNKNOWN));
        generateLaunchScript(scriptStrategy, packDirectory, postInstallDetection, warnings);

        // Build final result with accumulated warnings
        return new LoaderDetectionResult(
                postInstallDetection.loaderType(),
                postInstallDetection.loaderVersion(),
                postInstallDetection.minecraftVersion(),
                postInstallDetection.installerJar(),
                postInstallDetection.serverJar(),
                postInstallDetection.hasLibraries() || Files.isDirectory(packDirectory.resolve("libraries")),
                postInstallDetection.isLegacyForge(),
                List.copyOf(warnings)
        );
    }

    private void runInstaller(LoaderStrategy strategy, Path installerJar, Path packDir, List<String> warnings) {
        logger.info("Running installer {} in {}", installerJar.getFileName(), packDir);
        try {
            var javaExe = Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
            var args = new ArrayList<>(strategy.installerArguments(installerJar, packDir));
            // Replace "java" placeholder with absolute host java path
            if (!args.isEmpty() && "java".equals(args.getFirst())) {
                args.set(0, javaExe);
            }

            var pb = new ProcessBuilder(args);
            pb.directory(packDir.toFile());
            pb.redirectErrorStream(true);

            var process = pb.start();
            captureProcessOutput(process.getInputStream(), "installer");

            var finished = process.waitFor(INSTALLER_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                var message = "Installer timed out after " + INSTALLER_TIMEOUT_MINUTES + " minutes.";
                logger.warn(message);
                warnings.add(message);
                return;
            }

            var exitCode = process.exitValue();
            if (exitCode != 0) {
                var message = "Installer exited with code " + exitCode + ". Server may not be fully set up.";
                logger.warn(message);
                warnings.add(message);
            } else {
                logger.info("Installer completed successfully.");
                // Clean up installer JAR to save disk space
                deleteInstallerQuietly(installerJar);
            }
        } catch (IOException e) {
            var message = "Failed to run installer: " + e.getMessage();
            logger.warn(message, e);
            warnings.add(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("Installer was interrupted.");
        }
    }

    private void downloadAndRunInstaller(LoaderStrategy strategy, LoaderDetectionResult detection,
                                         Path packDir, List<String> warnings) {
        var mcVersion = detection.minecraftVersion();
        if (mcVersion == null) {
            warnings.add("Cannot download installer: Minecraft version is unknown. Please set it in the metadata.");
            return;
        }

        logger.info("Attempting to download {} installer for MC {}.", detection.loaderType(), mcVersion);

        // Resolve latest loader version
        var loaderVersion = detection.loaderVersion();
        if (loaderVersion == null) {
            var resolved = strategy.resolveLatestVersion(mcVersion, httpClient);
            if (resolved.isEmpty()) {
                warnings.add("Could not resolve latest " + detection.loaderType()
                        + " version for MC " + mcVersion + ". Skipping installer download.");
                return;
            }
            loaderVersion = resolved.get();
            logger.info("Resolved latest {} version for MC {}: {}", detection.loaderType(), mcVersion, loaderVersion);
        }

        // Build download URL
        var downloadUri = strategy.installerDownloadUri(mcVersion, loaderVersion);
        if (downloadUri.isEmpty()) {
            warnings.add("Could not construct installer download URL for " + detection.loaderType() + ".");
            return;
        }

        // Download
        var installerPath = packDir.resolve(DOWNLOADED_INSTALLER_NAME);
        try {
            downloadFile(downloadUri.get(), installerPath);
            logger.info("Downloaded installer to {}", installerPath);
        } catch (IOException e) {
            var message = "Failed to download installer from " + downloadUri.get() + ": " + e.getMessage();
            logger.warn(message, e);
            warnings.add(message);
            return;
        }

        // Run
        runInstaller(strategy, installerPath, packDir, warnings);
    }

    private void generateLaunchScript(LoaderStrategy strategy, Path packDir,
                                       LoaderDetectionResult detection, List<String> warnings) {
        try {
            var scriptContent = strategy.generateLaunchScript(packDir, detection);
            var scriptPath = packDir.resolve(MCMSM_LAUNCH_SCRIPT);
            Files.writeString(scriptPath, scriptContent);
            scriptPath.toFile().setExecutable(true);
            logger.info("Generated launch script at {}", scriptPath);
        } catch (IOException e) {
            var message = "Failed to generate launch script: " + e.getMessage();
            logger.warn(message, e);
            warnings.add(message);
        }
    }

    /**
     * Re-detects the loader after installer execution, since the installer may have
     * created new files (libraries/, run.sh, etc.).
     */
    private LoaderDetectionResult redetectIfNeeded(LoaderDetectionResult original, Path packDir) {
        if (original.installerJar() != null || original.loaderType() == LoaderType.UNKNOWN) {
            // Re-detect to pick up installer output
            var redetected = detector.detect(packDir);
            // Preserve original MC version if re-detection lost it
            var mcVersion = redetected.minecraftVersion() != null
                    ? redetected.minecraftVersion()
                    : original.minecraftVersion();
            var loaderVersion = redetected.loaderVersion() != null
                    ? redetected.loaderVersion()
                    : original.loaderVersion();
            return new LoaderDetectionResult(
                    redetected.loaderType() != LoaderType.UNKNOWN ? redetected.loaderType() : original.loaderType(),
                    loaderVersion,
                    mcVersion,
                    redetected.installerJar(),
                    redetected.serverJar(),
                    redetected.hasLibraries(),
                    redetected.isLegacyForge(),
                    redetected.warnings()
            );
        }
        return original;
    }

    private void downloadFile(java.net.URI uri, Path target) throws IOException {
        logger.info("Downloading {} to {}", uri, target);
        var tempPath = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            var request = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/octet-stream")
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Download failed with HTTP status " + response.statusCode());
            }

            try (var in = response.body()) {
                Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tempPath, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted.", e);
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
                // Best effort cleanup
            }
        }
    }

    private void captureProcessOutput(InputStream inputStream, String label) {
        Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("[{}] {}", label, line);
                }
            } catch (IOException e) {
                logger.debug("Error reading {} output: {}", label, e.getMessage());
            }
        });
    }

    private void deleteInstallerQuietly(Path installerJar) {
        try {
            Files.deleteIfExists(installerJar);
            logger.debug("Deleted installer JAR: {}", installerJar);
        } catch (IOException e) {
            logger.debug("Could not delete installer JAR {}: {}", installerJar, e.getMessage());
        }
    }
}
