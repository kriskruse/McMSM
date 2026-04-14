package dk.mcmsm.controller;

import dk.mcmsm.dto.responses.UpdateStatusResponse;
import dk.mcmsm.services.UpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * REST endpoints for application self-update.
 */
@RestController
@RequestMapping("/api/update")
public class UpdateController {

    private static final Logger logger = LoggerFactory.getLogger(UpdateController.class);

    private final UpdateService updateService;

    public UpdateController(UpdateService updateService) {
        this.updateService = updateService;
    }

    /**
     * Returns the running application version.
     *
     * @return version string.
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(Map.of("version", updateService.getCurrentVersion()));
    }

    /**
     * Checks GitHub for available updates. Cached per configured interval.
     *
     * @return update status with version info and download URL.
     */
    @GetMapping("/check")
    public ResponseEntity<UpdateStatusResponse> checkForUpdates() {
        try {
            var status = updateService.checkForUpdates(false);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Failed to check for updates.", e);
            return ResponseEntity.internalServerError().body(dummyStatus());
        }
    }

    /**
     * Checks GitHub for available updates. Forces a remote check, ignoring any cached results.
     *
     * @return update status with version info and download URL.
     */
    @GetMapping("/checkNow")
    public ResponseEntity<UpdateStatusResponse> checkForUpdatesNow() {
        try {
            var status = updateService.checkForUpdates(true);
            logger.info("Forced check update status - Found: {}", status);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Forced update check failed.", e);
            return ResponseEntity.internalServerError().body(dummyStatus());
        }
    }

    private UpdateStatusResponse dummyStatus() {
        return new UpdateStatusResponse(
                updateService.getCurrentVersion(),
                null,
                0,
                0,
                0,
                false,
                null
        );
    }

    /**
     * Downloads the latest release and initiates a rolling restart.
     * Returns 200 immediately; the server will shut down shortly after.
     *
     * @return confirmation message.
     */
    @PostMapping("/apply")
    public ResponseEntity<Map<String, String>> applyUpdate() {
        try {
            updateService.applyUpdate();
            return ResponseEntity.ok(Map.of("message", "Update initiated. The server will restart shortly."));
        } catch (IllegalStateException e) {
            logger.warn("Update rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            logger.error("Update failed.", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Update failed: " + e.getMessage()));
        }
    }
}
