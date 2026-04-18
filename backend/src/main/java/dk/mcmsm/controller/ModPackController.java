package dk.mcmsm.controller;

import dk.mcmsm.dto.requests.CommandRequestDto;
import dk.mcmsm.dto.requests.ModPackMetadataRequestDto;
import dk.mcmsm.dto.responses.ContainerStatsResponseDto;
import dk.mcmsm.dto.responses.ModPackDeployResponseDto;
import dk.mcmsm.dto.responses.ModPackMetadataResponseDto;
import dk.mcmsm.dto.responses.ModPackUploadResponseDto;
import dk.mcmsm.entities.ModPack;
import dk.mcmsm.services.ContainerStatsService;
import dk.mcmsm.services.McModPackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * REST controller for modpack lifecycle operations.
 * Exception handling is delegated to {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/modpacks")
public class ModPackController {
    private static final Logger logger = LoggerFactory.getLogger(ModPackController.class);

    private final McModPackService mcModPackService;
    private final ContainerStatsService containerStatsService;

    public ModPackController(McModPackService mcModPackService, ContainerStatsService containerStatsService) {
        this.mcModPackService = mcModPackService;
        this.containerStatsService = containerStatsService;
    }

    @GetMapping("/")
    public ResponseEntity<List<ModPack>> getAllPacks() {
        return ResponseEntity.ok(mcModPackService.getAllPacks());
    }

    @GetMapping("/saved")
    public ResponseEntity<List<ModPack>> getSavedPacks() {
        return ResponseEntity.ok(mcModPackService.getSavedPacks());
    }

    @GetMapping("/deployed")
    public ResponseEntity<List<ModPack>> getDeployedPacks() {
        return ResponseEntity.ok(mcModPackService.getDeployedPacks());
    }

    @GetMapping("/{packId}/stats")
    public ResponseEntity<ContainerStatsResponseDto> getPackStats(@PathVariable Long packId) {
        return containerStatsService.getStats(packId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(value = "/{packId}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPackLogs(
            @PathVariable Long packId,
            @RequestParam(name = "tail", defaultValue = "200") Integer tail
    ) {
        logger.debug("Log fetch requested for modpack packId={}, tail={}", packId, tail);
        return ResponseEntity.ok(mcModPackService.getPackLogs(packId, tail));
    }

    /**
     * Streams live Docker container logs via Server-Sent Events.
     *
     * @param packId the modpack ID.
     * @param tail   number of initial log lines.
     * @return SSE emitter streaming log events.
     */
    @GetMapping(value = "/{packId}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPackLogs(
            @PathVariable Long packId,
            @RequestParam(name = "tail", defaultValue = "200") Integer tail
    ) {
        logger.info("SSE log stream requested for packId={}, tail={}", packId, tail);
        return mcModPackService.streamPackLogs(packId, tail);
    }

    /**
     * Sends a console command to a running modpack container.
     *
     * @param packId  the modpack ID.
     * @param request the command request body.
     * @return confirmation message.
     */
    @PostMapping("/{packId}/command")
    public ResponseEntity<Map<String, String>> sendCommand(
            @PathVariable Long packId,
            @RequestBody CommandRequestDto request
    ) {
        logger.info("Command sent to packId={}: '{}'", packId, request.command());
        mcModPackService.sendCommand(packId, request.command());
        return ResponseEntity.ok(Map.of("message", "Command sent."));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModPackUploadResponseDto> savePack(@RequestPart("file") MultipartFile file) {
        logger.info("Upload requested for modpack archive: originalName='{}', size={} bytes.", file.getOriginalFilename(), file.getSize());
        var response = mcModPackService.savePack(file);
        logger.info("Upload completed for modpack packId={}, name='{}'.", response.packId(), response.name());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{packId}/metadata")
    public ResponseEntity<ModPackMetadataResponseDto> updateMetadata(
            @PathVariable Long packId,
            @RequestBody ModPackMetadataRequestDto metadataRequest
    ) {
        logger.info("Metadata update requested for modpack packId={}", packId);
        var response = mcModPackService.updateMetadata(packId, metadataRequest);
        logger.info("Metadata update completed for modpack packId={}", packId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{packId}/deploy")
    public ResponseEntity<ModPackDeployResponseDto> deployPack(@PathVariable Long packId) {
        logger.info("Deployment requested for modpack packId={}", packId);
        var response = mcModPackService.deployPack(packId);
        logger.info("Deployment completed for modpack packId={}, containerId='{}'", packId, response.containerId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{packId}/start")
    public ResponseEntity<ModPackDeployResponseDto> startPack(@PathVariable Long packId) {
        logger.info("Start requested for modpack packId={}", packId);
        var response = mcModPackService.startPack(packId);
        logger.info("Start completed for modpack packId={}, containerId='{}'", packId, response.containerId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{packId}/stop")
    public ResponseEntity<ModPackDeployResponseDto> stopPack(@PathVariable Long packId) {
        logger.info("Stop requested for modpack packId={}", packId);
        var response = mcModPackService.stopPack(packId);
        logger.info("Stop completed for modpack packId={}, containerId='{}'", packId, response.containerId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{packId}/archive")
    public ResponseEntity<ModPackDeployResponseDto> archivePack(@PathVariable Long packId) {
        logger.info("Archive requested for modpack packId={}", packId);
        var response = mcModPackService.archivePack(packId);
        logger.info("Archive completed for modpack packId={}", packId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/{packId}/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModPackUploadResponseDto> updatePack(@PathVariable Long packId, @RequestPart("file") MultipartFile file) {
        logger.info("Update requested for modpack packId={}", packId);
        var response = mcModPackService.updatePack(packId, file);
        logger.info("Update completed for modpack packId={}", packId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePack(@RequestParam Long packId) {
        logger.info("Delete requested for modpack packId={}", packId);
        mcModPackService.deletePack(packId);
        logger.info("Delete completed for modpack packId={}", packId);
        return ResponseEntity.ok("Mod pack deleted successfully!");
    }
}
