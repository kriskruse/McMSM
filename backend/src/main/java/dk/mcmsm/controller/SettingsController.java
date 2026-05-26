package dk.mcmsm.controller;

import dk.mcmsm.dto.requests.SettingsUpdateRequestDto;
import dk.mcmsm.dto.responses.SettingsResponseDto;
import dk.mcmsm.services.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for reading and updating application settings.
 * Secret values are never returned in the response — only flags indicating
 * whether each secret is configured.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    private final SettingsService settingsService;

    /**
     * Creates the controller.
     *
     * @param settingsService settings business logic
     */
    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Returns the current settings with secret values masked.
     *
     * @return response with configuration flags
     */
    @GetMapping
    public ResponseEntity<SettingsResponseDto> get() {
        return ResponseEntity.ok(new SettingsResponseDto(settingsService.isCurseforgeApiKeyConfigured()));
    }

    /**
     * Applies a partial update to the settings. Fields left {@code null} on the
     * request are unchanged; empty strings clear the field.
     *
     * @param request update payload
     * @return updated settings with configuration flags
     */
    @PutMapping
    public ResponseEntity<SettingsResponseDto> update(@RequestBody SettingsUpdateRequestDto request) {
        logger.info("Settings update requested.");
        settingsService.updateCurseforgeApiKey(request.curseforgeApiKey());
        return ResponseEntity.ok(new SettingsResponseDto(settingsService.isCurseforgeApiKeyConfigured()));
    }
}
