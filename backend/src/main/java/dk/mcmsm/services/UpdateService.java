package dk.mcmsm.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dk.mcmsm.config.UpdateProperties;
import dk.mcmsm.dto.responses.UpdateStatusResponse;
import dk.mcmsm.exception.RunningInDevModeException;
import dk.mcmsm.util.Globals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Handles checking GitHub for new releases, downloading updates,
 * generating platform-specific updater scripts, and triggering application restart.
 */
@Service
public class UpdateService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com/repos/";
    private static final int HEALTH_CHECK_RETRIES = 30;
    private static final int HEALTH_CHECK_DELAY_SECONDS = 2;
    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    private final UpdateProperties properties;
    private final ConfigurableApplicationContext applicationContext;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ReentrantLock updateLock = new ReentrantLock();

    private volatile UpdateStatusResponse cachedStatus;
    private volatile Instant cacheExpiry = Instant.EPOCH;

    /**
     * Creates a new UpdateService.
     *
     * @param properties         update configuration properties.
     * @param applicationContext used to trigger graceful shutdown after update.
     */
    public UpdateService(UpdateProperties properties,
                         ConfigurableApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    /**
     * Checks GitHub for available updates. Results are cached for the interval
     * defined by {@link UpdateProperties#checkIntervalMs()}.
     *
     * @return update status including version info and download URL.
     */
    public UpdateStatusResponse checkForUpdates(boolean forceUpdate) throws RuntimeException {
        if (!forceUpdate && cachedStatus != null && Instant.now().isBefore(cacheExpiry)) {
            return cachedStatus;
        }

        String currentVersion = Globals.APP_VERSION;

        if (Globals.DEV_VERSION.equals(currentVersion)) {
            UpdateStatusResponse devStatus = new UpdateStatusResponse(Globals.DEV_VERSION, Globals.DEV_VERSION, 0, 0, 0, 0, false, null);
            cachedStatus = devStatus;
            cacheExpiry = Instant.now().plusMillis(properties.checkIntervalMs());
            return devStatus;
        }

        try {
            List<GitHubRelease> releases = fetchGitHubReleases();
            UpdateStatusResponse status = buildStatusFromReleases(currentVersion, releases);
            cachedStatus = status;
            cacheExpiry = Instant.now().plusMillis(properties.checkIntervalMs());
            return status;
        } catch (RunningInDevModeException e) {
            logger.warn("Update check skipped: running outside a JAR (dev mode).");
            UpdateStatusResponse devStatus = new UpdateStatusResponse(currentVersion, currentVersion, 0, 0, 0, 0, false, null);
            cachedStatus = devStatus;
            cacheExpiry = Instant.now().plusMillis(properties.checkIntervalMs());
            return devStatus;
        } catch (Exception e) {
            logger.error("Failed to check for updates from GitHub.", e);
            throw new RuntimeException("Failed to check for updates.", e);
        }
    }

    /**
     * Downloads the latest release JAR, writes a platform-specific updater script,
     * launches it, and initiates a graceful application shutdown.
     *
     * @throws IllegalStateException if not running from a JAR or no update is available.
     * @throws IOException           if the download or script creation fails.
     */
    public void applyUpdate() throws IOException {
        if (!updateLock.tryLock()) {
            throw new IllegalStateException("An update is already in progress.");
        }

        try {
            if (!Globals.IS_RUNNING_FROM_JAR) {
                throw new IllegalStateException("Cannot self-update when not running from a JAR.");
            }

            UpdateStatusResponse status = checkForUpdates(true);
            if (!status.updateAvailable()) {
                throw new IllegalStateException("No update available.");
            }

            logger.info("Running in Jar, resolving path.");
            Path currentJarPath = Globals.WORKING_DIRECTORY;
            Path jarDirectory = currentJarPath.getParent();
            String newJarName = "McMSM-" + status.latestVersion() + ".jar";
            Path newJarPath = jarDirectory.resolve(newJarName);

            logger.info("Downloading update {} to {}", status.latestVersion(), newJarPath);
            downloadRelease(status.downloadUrl(), newJarPath);

            Path metadataPath = jarDirectory.resolve("update-pending.json");
            writeUpdateMetadata(metadataPath, currentJarPath, newJarPath);

            Path scriptPath = writeUpdaterScript(jarDirectory, currentJarPath, newJarPath);
            launchUpdaterScript(scriptPath);

            logger.info("Update downloaded. Initiating shutdown for restart.");
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                applicationContext.close();
            });
        } finally {
            updateLock.unlock();
        }
    }

    private List<GitHubRelease> fetchGitHubReleases() throws IOException, InterruptedException {
        URI uri = URI.create(GITHUB_API_BASE + properties.githubRepo() + "/releases");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub API returned status " + response.statusCode());
        }

        JsonArray jsonArray = gson.fromJson(response.body(), JsonArray.class);
        ArrayList<GitHubRelease> releases = new ArrayList<>();

        for (JsonElement element : jsonArray) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.get("draft").getAsBoolean()) {
                continue;
            }

            String tagName = obj.get("tag_name").getAsString();
            String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String publishedAt = obj.get("published_at").getAsString();
            String downloadUrl = extractJarDownloadUrl(obj);

            releases.add(new GitHubRelease(version, tagName, publishedAt, downloadUrl));
        }

        releases.sort(Comparator.comparing(GitHubRelease::publishedAt).reversed());
        return releases;
    }

    private String extractJarDownloadUrl(JsonObject releaseObj) {
        JsonArray assets = releaseObj.getAsJsonArray("assets");
        if (assets == null) {
            return null;
        }
        for (JsonElement asset : assets) {
            JsonObject assetObj = asset.getAsJsonObject();
            String name = assetObj.get("name").getAsString();
            if (name.endsWith(".jar")) {
                return assetObj.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private UpdateStatusResponse buildStatusFromReleases(String currentVersion, List<GitHubRelease> releases) throws RunningInDevModeException {
        if (!Globals.IS_RUNNING_FROM_JAR) {
            throw new RunningInDevModeException(this.getClass());
        }

        if (releases.isEmpty()) {
            return new UpdateStatusResponse(currentVersion, currentVersion, 0, 0, 0, 0, false, null);
        }

        GitHubRelease latest = releases.getFirst();
        int currentIndex = -1;
        for (int i = 0; i < releases.size(); i++) {
            if (releases.get(i).version().equals(currentVersion)) {
                currentIndex = i;
                break;
            }
        }

        int versionsBehind;
        if (currentIndex == -1) {
            versionsBehind = releases.size();
        } else {
            versionsBehind = currentIndex;
        }

        int majorBehind = 0;
        int minorBehind = 0;
        int patchBehind = 0;
        int newerCount = currentIndex == -1 ? releases.size() : currentIndex;
        for (int i = 0; i < newerCount; i++) {
            String version = releases.get(i).version();
            if (isPatchRelease(version)) {
                patchBehind++;
            } else if (isMajorRelease(version)) {
                majorBehind++;
            } else {
                minorBehind++;
            }
        }

        boolean updateAvailable = versionsBehind > 0 && latest.downloadUrl() != null;

        return new UpdateStatusResponse(
                currentVersion,
                latest.version(),
                versionsBehind,
                majorBehind,
                minorBehind,
                patchBehind,
                updateAvailable,
                latest.downloadUrl()
        );
    }



    private void downloadRelease(String downloadUrl, Path targetPath) throws IOException {
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                    .header("Accept", "application/octet-stream")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Download failed with status " + response.statusCode());
            }

            try (InputStream in = response.body()) {
                Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted.", e);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private void writeUpdateMetadata(Path metadataPath, Path oldJar, Path newJar) throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("oldJar", oldJar.toAbsolutePath().toString());
        metadata.addProperty("newJar", newJar.toAbsolutePath().toString());
        metadata.addProperty("pid", Globals.PROCESS_ID);
        metadata.addProperty("timestamp", Instant.now().toString());
        Files.writeString(metadataPath, gson.toJson(metadata));
    }

    private Path writeUpdaterScript(Path directory, Path oldJar, Path newJar) throws IOException {
        logger.info("Determining OS for updater script generation.");
        if (Globals.IS_WINDOWS) {
            logger.info("Found os Windows, using windows script: {}", directory);
            return writeWindowsUpdaterScript(directory, oldJar, newJar);
        } else {
            logger.info("Found os as Other, using unix script: {}", directory);
            return writeUnixUpdaterScript(directory, oldJar, newJar);
        }
    }

    private Path writeWindowsUpdaterScript(Path directory, Path oldJar, Path newJar) throws IOException {
        logger.info("Creating Windows updater script in directory: {}", directory);
        Path scriptPath = directory.resolve("mcmsm-updater.bat");

        String script = """
                @echo off
                setlocal
                set "OLD_PID=%d"
                set "OLD_JAR=%s"
                set "NEW_JAR=%s"
                set "JAVA_EXE=%s"
                set "PORT=%s"
                set "RETRIES=%d"
                set "DELAY=%d"

                echo [McMSM Updater] Waiting for old process (PID %%OLD_PID%%) to exit...
                :WAIT_LOOP
                tasklist /fi "PID eq %%OLD_PID%%" 2>nul | find "%%OLD_PID%%" >nul
                if %%errorlevel%%==0 (
                    timeout /t 1 /nobreak >nul
                    goto WAIT_LOOP
                )
                echo [McMSM Updater] Old process exited. Starting new version...

                start "" "%%JAVA_EXE%%" -jar "%%NEW_JAR%%"

                echo [McMSM Updater] Waiting for new version to become healthy...
                set /a COUNT=0
                :HEALTH_LOOP
                if %%COUNT%% geq %%RETRIES%% goto HEALTH_FAIL
                timeout /t %%DELAY%% /nobreak >nul
                curl -sf http://localhost:%%PORT%%/api/health >nul 2>&1
                if %%errorlevel%%==0 goto HEALTH_OK
                set /a COUNT+=1
                goto HEALTH_LOOP

                :HEALTH_OK
                echo [McMSM Updater] New version is healthy. Update complete.
                goto END

                :HEALTH_FAIL
                echo [McMSM Updater] New version failed health check. Restarting old version...
                taskkill /f /im java.exe >nul 2>&1
                timeout /t 2 /nobreak >nul
                start "" "%%JAVA_EXE%%" -jar "%%OLD_JAR%%"
                del "%%NEW_JAR%%" >nul 2>&1
                echo [McMSM Updater] Rollback complete.

                :END
                endlocal
                """.formatted(Globals.PROCESS_ID, oldJar.toAbsolutePath(), newJar.toAbsolutePath(),
                Globals.JAVA_EXE, Globals.SERVER_PORT, HEALTH_CHECK_RETRIES, HEALTH_CHECK_DELAY_SECONDS);

        Files.writeString(scriptPath, script);
        logger.info("Windows updater script written to: {}", scriptPath);
        return scriptPath;
    }

    private Path writeUnixUpdaterScript(Path directory, Path oldJar, Path newJar) throws IOException {
        logger.info("Creating Unix updater script in directory: {}", directory);
        Path scriptPath = directory.resolve("mcmsm-updater.sh");

        String script = """
                #!/usr/bin/env bash
                set -euo pipefail

                OLD_PID=%d
                OLD_JAR="%s"
                NEW_JAR="%s"
                JAVA_EXE="%s"
                PORT="%s"
                RETRIES=%d
                DELAY=%d
                APP_LOG="$(dirname "$NEW_JAR")/mcmsm-app.log"

                launch_jar() {
                    local jar="$1"
                    if [ -t 1 ]; then
                        "$JAVA_EXE" -jar "$jar" &
                        echo "[McMSM Updater] Process output visible in this terminal."
                    else
                        nohup "$JAVA_EXE" -jar "$jar" > "$APP_LOG" 2>&1 &
                        echo "[McMSM Updater] Headless mode. Output: $APP_LOG"
                    fi
                }

                echo "[McMSM Updater] Waiting for old process (PID $OLD_PID) to exit..."
                while kill -0 "$OLD_PID" 2>/dev/null; do
                    sleep 1
                done
                echo "[McMSM Updater] Old process exited. Starting new version..."

                launch_jar "$NEW_JAR"
                NEW_PID=$!

                echo "[McMSM Updater] Waiting for new version to become healthy..."
                COUNT=0
                while [ "$COUNT" -lt "$RETRIES" ]; do
                    sleep "$DELAY"
                    if curl -sf "http://localhost:${PORT}/api/health" >/dev/null 2>&1; then
                        echo "[McMSM Updater] New version is healthy. Update complete."
                        exit 0
                    fi
                    COUNT=$((COUNT + 1))
                done

                echo "[McMSM Updater] New version failed health check. Rolling back..."
                kill "$NEW_PID" 2>/dev/null || true
                sleep 2
                launch_jar "$OLD_JAR"
                rm -f "$NEW_JAR"
                echo "[McMSM Updater] Rollback complete."
                """.formatted(Globals.PROCESS_ID, oldJar.toAbsolutePath(), newJar.toAbsolutePath(),
                Globals.JAVA_EXE, Globals.SERVER_PORT, HEALTH_CHECK_RETRIES, HEALTH_CHECK_DELAY_SECONDS);

        Files.writeString(scriptPath, script);
        scriptPath.toFile().setExecutable(true);
        logger.info("Unix updater script written to: {}", scriptPath);
        return scriptPath;
    }

    private void launchUpdaterScript(Path scriptPath) throws IOException {
        logger.info("Attempting to launch updater script: {}", scriptPath);
        ProcessBuilder pb;
        if (Globals.IS_WINDOWS) {
            pb = new ProcessBuilder("cmd.exe", "/c", scriptPath.toAbsolutePath().toString());
            pb.directory(scriptPath.getParent().toFile());
            pb.redirectOutput(scriptPath.resolveSibling("mcmsm-updater.log").toFile());
            pb.redirectErrorStream(true);
        } else {
            var terminal = detectLinuxTerminalEmulator();
            pb = buildUnixUpdaterProcessBuilder(terminal, scriptPath);
            pb.directory(scriptPath.getParent().toFile());
            if (terminal == null) {
                pb.redirectOutput(scriptPath.resolveSibling("mcmsm-updater.log").toFile());
                pb.redirectErrorStream(true);
            }
        }
        logger.info("Launching updater script with command: {}", String.join(" ", pb.command()));
        pb.start();
    }

    /**
     * Detects an available terminal emulator on Linux when a display server is present.
     *
     * @return the terminal emulator command name, or {@code null} if headless or none found
     */
    private String detectLinuxTerminalEmulator() {
        var display = System.getenv("DISPLAY");
        var waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        if ((display == null || display.isBlank()) && (waylandDisplay == null || waylandDisplay.isBlank())) {
            logger.info("No display server detected (headless). Will run updater without terminal.");
            return null;
        }

        var candidates = List.of("gnome-terminal", "konsole", "xfce4-terminal", "xterm", "x-terminal-emulator");
        for (var candidate : candidates) {
            try {
                var check = new ProcessBuilder("which", candidate)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectErrorStream(true)
                        .start();
                if (check.waitFor() == 0) {
                    logger.info("Detected terminal emulator: {}", candidate);
                    return candidate;
                }
            } catch (IOException | InterruptedException e) {
                logger.debug("Failed to check for terminal emulator '{}': {}", candidate, e.getMessage());
            }
        }
        logger.info("No terminal emulator found. Will run updater without terminal.");
        return null;
    }

    private ProcessBuilder buildUnixUpdaterProcessBuilder(String terminal, Path scriptPath) {
        var script = scriptPath.toAbsolutePath().toString();
        if (terminal == null) {
            return new ProcessBuilder("bash", script);
        }
        return switch (terminal) {
            case "gnome-terminal" -> new ProcessBuilder(terminal, "--title=McMSM Server", "--", "bash", script);
            case "konsole" -> new ProcessBuilder(terminal, "-e", "bash", script);
            case "xfce4-terminal" -> new ProcessBuilder(terminal, "--title=McMSM Server", "-e", "bash " + script);
            case "xterm" -> new ProcessBuilder(terminal, "-title", "McMSM Server", "-e", "bash", script);
            case "x-terminal-emulator" -> new ProcessBuilder(terminal, "-e", "bash", script);
            default -> new ProcessBuilder("bash", script);
        };
    }

    /**
     * A patch/hotfix release has 3 segments (e.g. "2.0.1").
     */
    private static boolean isPatchRelease(String version) {
        return version.chars().filter(c -> c == '.').count() >= 2;
    }

    /**
     * A major release has minor version 0 and no patch (e.g. "2.0"). Everything else is minor/prerelease.
     */
    private static boolean isMajorRelease(String version) {
        int dot = version.indexOf('.');
        if (dot == -1) {
            return true;
        }
        String rest = version.substring(dot + 1);
        return "0".equals(rest);
    }

    private record GitHubRelease(String version, String tagName, String publishedAt, String downloadUrl) {
    }
}
