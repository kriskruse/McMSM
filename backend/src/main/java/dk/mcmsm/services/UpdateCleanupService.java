package dk.mcmsm.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dk.mcmsm.config.UpdateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import dk.mcmsm.util.Globals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cleans up artefacts from a previous self-update on application startup.
 * <p>
 * After a successful update the old JAR, the updater script, and the
 * {@code update-pending.json} marker file are no longer needed.
 * This service removes them once the new version is confirmed running.
 */
@Service
public class UpdateCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateCleanupService.class);
    private static final int DELETE_RETRY_COUNT = 5;
    private static final long DELETE_RETRY_DELAY_MS = 2000;

    private final UpdateProperties properties;
    private final Gson gson = new Gson();

    /**
     * Creates a new cleanup service.
     *
     * @param properties update configuration (used to locate data root indirectly).
     */
    public UpdateCleanupService(UpdateProperties properties) {
        this.properties = properties;
    }

    /**
     * Runs after the application context is fully initialised.
     * Checks for an {@code update-pending.json} file next to the running JAR
     * and, if found, removes the old JAR and helper files.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupAfterUpdate() {
        if (!Globals.IS_RUNNING_FROM_JAR) {
            return;
        }

        var jarDirectory = Globals.WORKING_DIRECTORY.getParent();
        var metadataPath = jarDirectory.resolve("update-pending.json");
        if (!Files.exists(metadataPath)) {
            return;
        }

        try {
            var content = Files.readString(metadataPath);
            var metadata = gson.fromJson(content, JsonObject.class);
            var oldJarStr = metadata.get("oldJar").getAsString();
            var oldJar = Path.of(oldJarStr);

            logger.info("Post-update cleanup: removing old JAR {}", oldJar);
            deleteWithRetry(oldJar);
            deleteQuietly(jarDirectory.resolve("mcmsm-updater.bat"));
            deleteQuietly(jarDirectory.resolve("mcmsm-updater.sh"));
            deleteQuietly(jarDirectory.resolve("mcmsm-updater.log"));
            deleteQuietly(jarDirectory.resolve("mcmsm-updater.started"));
            deleteQuietly(metadataPath);

            logger.info("Post-update cleanup completed successfully.");
        } catch (Exception e) {
            logger.warn("Post-update cleanup failed. Old files may remain.", e);
        }
    }

    private void deleteWithRetry(Path file) {
        for (int i = 0; i < DELETE_RETRY_COUNT; i++) {
            try {
                Files.deleteIfExists(file);
                return;
            } catch (IOException e) {
                logger.debug("Retry {}/{} deleting {}: {}", i + 1, DELETE_RETRY_COUNT, file, e.getMessage());
                try {
                    Thread.sleep(DELETE_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        logger.warn("Could not delete old JAR after {} retries: {}", DELETE_RETRY_COUNT, file);
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.debug("Could not delete {}: {}", file, e.getMessage());
        }
    }
}
