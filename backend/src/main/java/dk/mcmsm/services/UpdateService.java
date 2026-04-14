package dk.mcmsm.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dk.mcmsm.config.UpdateProperties;
import dk.mcmsm.dto.responses.UpdateStatusResponse;
import dk.mcmsm.exception.RunningInDevModeException;
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
    private static final String DEV_VERSION = "dev";
    private static final int HEALTH_CHECK_RETRIES = 30;
    private static final int HEALTH_CHECK_DELAY_SECONDS = 2;
    private static final int SHUTDOWN_WAIT_SECONDS = 30;

    private final UpdateProperties properties;
    private final BuildProperties buildProperties;
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
     * @param buildProperties    Spring Boot build info (provides the artifact version).
     * @param applicationContext used to trigger graceful shutdown after update.
     */
    public UpdateService(UpdateProperties properties,
                         BuildProperties buildProperties,
                         ConfigurableApplicationContext applicationContext) {
        this.properties = properties;
        this.buildProperties = buildProperties;
        this.applicationContext = applicationContext;
    }

    /**
     * Returns the version of the currently running application.
     * Returns {@value DEV_VERSION} when running outside a packaged JAR.
     *
     * @return current version string.
     */
    public String getCurrentVersion() {
        String version = buildProperties.getVersion();
        return version != null ? version : DEV_VERSION;
    }

    private String resolveWorkingPath(){
        return getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
    }

    /**
     * Returns {@code true} if the application is running from a packaged JAR file
     * rather than from an IDE or {@code mvn spring-boot:run}.
     *
     * @return whether a self-update is structurally possible.
     */
    public boolean isRunningFromJar() {
        return resolveWorkingPath().contains(".jar");
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

        String currentVersion = getCurrentVersion();

        if (DEV_VERSION.equals(currentVersion)) {
            UpdateStatusResponse devStatus = new UpdateStatusResponse(DEV_VERSION, DEV_VERSION, 0, 0, 0, false, null);
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
            UpdateStatusResponse devStatus = new UpdateStatusResponse(currentVersion, currentVersion, 0, 0, 0, false, null);
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
            if (!isRunningFromJar()) {
                throw new IllegalStateException("Cannot self-update when not running from a JAR.");
            }

            UpdateStatusResponse status = checkForUpdates(true);
            if (!status.updateAvailable()) {
                throw new IllegalStateException("No update available.");
            }

            logger.info("Running in Jar, resolving path.");
            Path currentJarPath = workingDirAsPath();
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
        if (!isRunningFromJar()) {
            throw new RunningInDevModeException(this.getClass());
        }

        if (releases.isEmpty()) {
            return new UpdateStatusResponse(currentVersion, currentVersion, 0, 0, 0, false, null);
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
        int newerCount = currentIndex == -1 ? releases.size() : currentIndex;
        for (int i = 0; i < newerCount; i++) {
            if (isMajorRelease(releases.get(i).version())) {
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
                updateAvailable,
                latest.downloadUrl()
        );
    }

    private Path workingDirAsPath() {

        try {
            String path = URLDecoder.decode(resolveWorkingPath(), StandardCharsets.UTF_8);

            if (path.startsWith("nested:")) {
                path = path.substring("nested:".length());
            }

            int jarIndex = path.indexOf(".jar");
            if (jarIndex != -1) {
                path = path.substring(0, jarIndex + 4);
            }
            logger.info("workingDirAsPath found path: {}", path);

            // On Windows, remove leading slash before drive letter (e.g., /C:/...)
            if (System.getProperty("os.name").toLowerCase().contains("win") && path.matches("^/[A-Za-z]:.*")) {
                path = path.substring(1);
            }
            return Path.of(path);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot resolve current JAR path.", e);
        }
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
        metadata.addProperty("pid", ProcessHandle.current().pid());
        metadata.addProperty("timestamp", Instant.now().toString());
        Files.writeString(metadataPath, gson.toJson(metadata));
    }

    private Path writeUpdaterScript(Path directory, Path oldJar, Path newJar) throws IOException {
        logger.info("Determining OS for updater script generation.");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
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
        long pid = ProcessHandle.current().pid();
        String javaHome = System.getProperty("java.home");
        String javaExe = Path.of(javaHome, "bin", "java.exe").toAbsolutePath().toString();
        String port = Optional.ofNullable(System.getProperty("server.port")).orElse("8080");

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
                """.formatted(pid, oldJar.toAbsolutePath(), newJar.toAbsolutePath(),
                javaExe, port, HEALTH_CHECK_RETRIES, HEALTH_CHECK_DELAY_SECONDS);

        Files.writeString(scriptPath, script);
        logger.info("Windows updater script written to: {}", scriptPath);
        return scriptPath;
    }

    private Path writeUnixUpdaterScript(Path directory, Path oldJar, Path newJar) throws IOException {
        logger.info("Creating Unix updater script in directory: {}", directory);
        Path scriptPath = directory.resolve("mcmsm-updater.sh");
        long pid = ProcessHandle.current().pid();
        String javaHome = System.getProperty("java.home");
        String javaExe = Path.of(javaHome, "bin", "java").toAbsolutePath().toString();
        String port = Optional.ofNullable(System.getProperty("server.port")).orElse("8080");

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

                echo "[McMSM Updater] Waiting for old process (PID $OLD_PID) to exit..."
                while kill -0 "$OLD_PID" 2>/dev/null; do
                    sleep 1
                done
                echo "[McMSM Updater] Old process exited. Starting new version..."

                "$JAVA_EXE" -jar "$NEW_JAR" &
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
                "$JAVA_EXE" -jar "$OLD_JAR" &
                rm -f "$NEW_JAR"
                echo "[McMSM Updater] Rollback complete."
                """.formatted(pid, oldJar.toAbsolutePath(), newJar.toAbsolutePath(),
                javaExe, port, HEALTH_CHECK_RETRIES, HEALTH_CHECK_DELAY_SECONDS);

        Files.writeString(scriptPath, script);
        scriptPath.toFile().setExecutable(true);
        logger.info("Unix updater script written to: {}", scriptPath);
        return scriptPath;
    }

    private void launchUpdaterScript(Path scriptPath) throws IOException {
        logger.info("Attempting to launch updater script: {}", scriptPath);
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb;
        if (isWindows) {
            pb = new ProcessBuilder("cmd.exe", "/c", scriptPath.toAbsolutePath().toString());
        } else {
            pb = new ProcessBuilder("bash", scriptPath.toAbsolutePath().toString());
        }
        pb.directory(scriptPath.getParent().toFile());
        pb.redirectOutput(scriptPath.resolveSibling("mcmsm-updater.log").toFile());
        pb.redirectErrorStream(true);
        logger.info("Launching updater script with command: {}", String.join(" ", pb.command()));
        pb.start();
    }

    /**
     * A major release has minor version 0 (e.g. "2.0"). Everything else is a minor/prerelease.
     */
    private static boolean isMajorRelease(String version) {
        int dot = version.indexOf('.');
        if (dot == -1) {
            return true;
        }
        String minor = version.substring(dot + 1);
        return "0".equals(minor);
    }

    private record GitHubRelease(String version, String tagName, String publishedAt, String downloadUrl) {
    }
}
