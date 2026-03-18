package dk.broegger_kruse.applicationdemo.controller;

import dk.broegger_kruse.applicationdemo.dto.requests.ModPackMetadataRequestDto;
import dk.broegger_kruse.applicationdemo.dto.responses.ModPackDeployResponseDto;
import dk.broegger_kruse.applicationdemo.dto.responses.ModPackMetadataResponseDto;
import dk.broegger_kruse.applicationdemo.dto.responses.ModPackUploadResponseDto;
import dk.broegger_kruse.applicationdemo.entities.ModPack;
import dk.broegger_kruse.applicationdemo.services.McModPackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/modpacks")
public class ModPackController {
    private static final Logger logger = LoggerFactory.getLogger(ModPackController.class);

    private final McModPackService mcModPackService;

    public ModPackController(McModPackService mcModPackService) {
        this.mcModPackService = mcModPackService;
    }

    @GetMapping("/")
    public ResponseEntity<List<ModPack>> getAllPacks() {
        logger.info("Fetching all modpacks.");
        List<ModPack> allPacks = mcModPackService.getAllPacks();
        logger.info("Fetched {} modpacks.", allPacks.size());
        return ResponseEntity.ok(allPacks);
    }

    @GetMapping("/saved")
    public ResponseEntity<List<ModPack>> getSavedPacks() {
        logger.info("Fetching saved modpacks.");
        List<ModPack> savedPacks = mcModPackService.getSavedPacks();
        logger.info("Fetched {} saved modpacks.", savedPacks.size());
        return ResponseEntity.ok(savedPacks);
    }

    @GetMapping("/deployed")
    public ResponseEntity<List<ModPack>> getDeployedPacks() {
        logger.info("Fetching deployed modpacks.");
        List<ModPack> deployedPacks = mcModPackService.getDeployedPacks();
        logger.info("Fetched {} deployed modpacks.", deployedPacks.size());
        return ResponseEntity.ok(deployedPacks);
    }

    @GetMapping(value = "/{packId}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPackLogs(
            @PathVariable Long packId,
            @RequestParam(name = "tail", defaultValue = "200") Integer tail
    ) {
        logger.debug("Log fetch requested for modpack packId={}, tail={}", packId, tail);
        try {
            var logs = mcModPackService.getPackLogs(packId, tail);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Log fetch failed for modpack packId={}", packId, e);
            return ResponseEntity.status(500).body("Failed to read mod pack logs: " + e.getMessage());
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModPackUploadResponseDto> savePack(@RequestPart("file") MultipartFile file) {
        logger.info("Upload requested for modpack archive: originalName='{}', size={} bytes.", file.getOriginalFilename(), file.getSize());
        try {
            ModPackUploadResponseDto uploadResponse = mcModPackService.savePack(file);
            logger.info("Upload completed for modpack packId={}, name='{}'.", uploadResponse.packId(), uploadResponse.name());
            return ResponseEntity.ok(uploadResponse);
        } catch (Exception e) {
            logger.error("Upload failed for archive originalName='{}'.", file.getOriginalFilename(), e);
            return ResponseEntity.status(500).body(new ModPackUploadResponseDto(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Failed to save mod pack: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{packId}/metadata")
    public ResponseEntity<ModPackMetadataResponseDto> updateMetadata(
            @PathVariable Long packId,
            @RequestBody ModPackMetadataRequestDto metadataRequest
    ) {
        logger.info("Metadata update requested for modpack packId={}", packId);
        try {
            ModPackMetadataResponseDto response = mcModPackService.updateMetadata(packId, metadataRequest);
            logger.info("Metadata update completed for modpack packId={}", packId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Metadata update failed for modpack packId={}", packId, e);
            return ResponseEntity.status(500).body(new ModPackMetadataResponseDto(
                    packId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Failed to update metadata: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{packId}/deploy")
    public ResponseEntity<ModPackDeployResponseDto> deployPack(@PathVariable Long packId) {
        logger.info("Deployment requested for modpack packId={}", packId);
        try {
            ModPackDeployResponseDto response = mcModPackService.deployPack(packId);
            logger.info("Deployment completed for modpack packId={}, containerId='{}'", packId, response.containerId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Deployment failed for modpack packId={}", packId, e);
            return ResponseEntity.status(500).body(new ModPackDeployResponseDto(
                    packId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "deploy_failed",
                    "Failed to deploy mod pack: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{packId}/start")
    public ResponseEntity<ModPackDeployResponseDto> startPack(@PathVariable Long packId) {
        logger.info("Start requested for modpack packId={}", packId);
        try {
            ModPackDeployResponseDto response = mcModPackService.startPack(packId);
            logger.info("Start completed for modpack packId={}, containerId='{}'", packId, response.containerId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Start failed for modpack packId={}", packId, e);
            return ResponseEntity.status(500).body(new ModPackDeployResponseDto(
                    packId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "error",
                    "Failed to start mod pack: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{packId}/stop")
    public ResponseEntity<ModPackDeployResponseDto> stopPack(@PathVariable Long packId) {
        logger.info("Stop requested for modpack packId={}", packId);
        try {
            ModPackDeployResponseDto response = mcModPackService.stopPack(packId);
            logger.info("Stop completed for modpack packId={}, containerId='{}'", packId, response.containerId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Stop failed for modpack packId={}", packId, e);
            return ResponseEntity.status(500).body(new ModPackDeployResponseDto(
                    packId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "error",
                    "Failed to stop mod pack: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{packId}/archive")
    public ResponseEntity<ModPackDeployResponseDto> archivePack(@PathVariable Long packId) {
        logger.info("Archive requested for modpack packId={}", packId);
        try {
            ModPackDeployResponseDto response = mcModPackService.archivePack(packId);
            logger.info("Archive completed for modpack packId={}", packId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Archive failed for modpack packId={}", packId, e);
            return ResponseEntity.status(500).body(new ModPackDeployResponseDto(
                    packId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "error",
                    "Failed to archive mod pack: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePack(@RequestParam Long packId) {
        logger.info("Delete requested for modpack packId={}", packId);
        try {
            mcModPackService.deletePack(packId);
        } catch (Exception e) {
            logger.error("Delete failed for modpack packId={}", packId, e);
            return ResponseEntity.status(500).body("Failed to delete mod pack: " + e.getMessage());
        }
        logger.info("Delete completed for modpack packId={}", packId);
        return ResponseEntity.ok("Mod pack deleted successfully!");
    }
}