package dk.mcmsm.services;

import dk.mcmsm.dto.requests.ModPackMetadataRequestDto;
import dk.mcmsm.dto.responses.ModPackDeployResponseDto;
import dk.mcmsm.dto.responses.ModPackMetadataResponseDto;
import dk.mcmsm.dto.responses.ModPackUploadResponseDto;
import dk.mcmsm.entities.ModPack;
import dk.mcmsm.repository.ModPackRepository;
import dk.mcmsm.services.ContainerService.DeploymentResult;
import dk.mcmsm.services.ContainerService.RuntimeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class McModPackService {
    private static final Logger logger = LoggerFactory.getLogger(McModPackService.class);
    private static final String DEFAULT_JAVA_XMX = "5G";
    private static final String UPLOAD_SUCCESS_MESSAGE = "Mod pack uploaded. Review metadata and submit corrections if needed.";
    private static final String METADATA_SUCCESS_MESSAGE = "Metadata updated successfully.";

    private final ContainerService containerService;
    private final ModPackRepository modPackRepository;
    private final FileService fileService;

    public McModPackService(ContainerService containerService, ModPackRepository modPackRepository, FileService fileService) {
        this.containerService = containerService;
        this.modPackRepository = modPackRepository;
        this.fileService = fileService;
    }

    public List<ModPack> getAllPacks() {
        removeMissingPackEntries();
        List<ModPack> packs = modPackRepository.findAll();
        logger.debug("Loaded {} modpacks from repository.", packs.size());
        return packs;
    }

    public List<ModPack> getSavedPacks() {
        removeMissingPackEntries();
        List<ModPack> packs = modPackRepository.getAllByIsDeployed(false);
        logger.debug("Loaded {} saved (not deployed) modpacks.", packs.size());
        return packs;
    }

    public List<ModPack> getDeployedPacks() {
        removeMissingPackEntries();
        List<ModPack> packs = modPackRepository.getAllByIsDeployed(true);
        logger.debug("Loaded {} deployed modpacks.", packs.size());
        return packs;
    }

    public ModPackUploadResponseDto savePack(MultipartFile file) {
        logger.info("Saving uploaded modpack file='{}' ({} bytes).", file.getOriginalFilename(), file.getSize());
        ModPack createdModPack = fileService.createDraftModPackFromFile(file);
        ModPack savedModPack = modPackRepository.save(createdModPack);
        fileService.assignImmutablePackDirectoryPath(savedModPack);
        savedModPack = modPackRepository.save(savedModPack);
        logger.info("Saved modpack packId={}, name='{}', path='{}'.", savedModPack.getPackId(), savedModPack.getName(), savedModPack.getPath());
        return new ModPackUploadResponseDto(savedModPack, UPLOAD_SUCCESS_MESSAGE);
    }

    public ModPackMetadataResponseDto updateMetadata(Long packId, ModPackMetadataRequestDto metadataRequest) {
        logger.info("Updating metadata for modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new RuntimeException("Mod pack not found with ID: " + packId));

        if (!fileService.packDirectoryExists(modPack)) {
            modPackRepository.delete(modPack);
            throw new IllegalStateException("Modpack files are missing for ID " + packId + ". The stale database entry was removed.");
        }

        applyMetadataUpdate(modPack, metadataRequest);
        modPack.setUpdatedAt(Instant.now());

        ModPack savedModPack = modPackRepository.save(modPack);
        logger.info("Metadata updated for modpack packId={}, name='{}', port='{}'.", savedModPack.getPackId(), savedModPack.getName(), savedModPack.getPort());
        return new ModPackMetadataResponseDto(savedModPack, METADATA_SUCCESS_MESSAGE);
    }

    public ModPackDeployResponseDto deployPack(Long packId) {
        logger.info("Deploying modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new RuntimeException("Mod pack not found with ID: " + packId));

        try {
            fileService.syncServerPortWithMetadata(modPack);
            int memoryLimitMiB = fileService.resolveContainerMemoryLimitMiB(modPack);
            DeploymentResult deploymentResult = containerService.deployServer(modPack, memoryLimitMiB);

            modPack.setContainerId(deploymentResult.containerId());
            modPack.setContainerName(deploymentResult.containerName());
            modPack.setIsDeployed(true);
            modPack.setStatus("running");
            modPack.setLastDeployAt(Instant.now());
            modPack.setLastDeployError(null);
            modPack.setUpdatedAt(Instant.now());

            ModPack savedModPack = modPackRepository.save(modPack);
            logger.info(
                    "Deployment succeeded for modpack packId={}, containerId='{}', image='{}', memoryLimitMiB={}",
                    savedModPack.getPackId(),
                    deploymentResult.containerId(),
                    deploymentResult.image(),
                    deploymentResult.memoryLimitMiB()
            );
            return new ModPackDeployResponseDto(
                    savedModPack.getPackId(),
                    savedModPack.getName(),
                    deploymentResult.containerId(),
                    deploymentResult.containerName(),
                    deploymentResult.image(),
                    deploymentResult.memoryLimitMiB(),
                    savedModPack.getStatus(),
                    "Mod pack deployed successfully."
            );
        } catch (Exception e) {
            modPack.setIsDeployed(false);
            modPack.setStatus("deploy_failed");
            modPack.setLastDeployError(e.getMessage());
            modPack.setLastDeployAt(Instant.now());
            modPack.setUpdatedAt(Instant.now());
            modPackRepository.save(modPack);
            logger.error("Deployment failed for modpack packId={}", packId, e);
            throw new RuntimeException("Failed to deploy mod pack with ID: " + packId, e);
        }
    }

    public ModPackDeployResponseDto startPack(Long packId) {
        logger.info("Starting modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new RuntimeException("Mod pack not found with ID: " + packId));

        try {
            RuntimeState runtimeState = containerService.startContainer(modPack);
            modPack.setContainerId(runtimeState.containerId());
            modPack.setContainerName(runtimeState.containerName());
            modPack.setIsDeployed(true);
            modPack.setStatus("running");
            modPack.setUpdatedAt(Instant.now());

            ModPack savedModPack = modPackRepository.save(modPack);
            logger.info("Start succeeded for modpack packId={}, containerId='{}'", savedModPack.getPackId(), savedModPack.getContainerId());
            return new ModPackDeployResponseDto(savedModPack, "Mod pack started successfully.");
        } catch (Exception e) {
            modPack.setStatus("error");
            modPack.setUpdatedAt(Instant.now());
            modPackRepository.save(modPack);
            logger.error("Start failed for modpack packId={}", packId, e);
            throw new RuntimeException("Failed to start mod pack with ID: " + packId, e);
        }
    }

    /**
     * Stops a modpack that is currently deployed and running in docker using the packId provided
     * @param packId Long pack id
     * @return ModPackDeployResponseDto
     */
    public ModPackDeployResponseDto stopPack(Long packId) {

        logger.info("Stopping modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new RuntimeException("Mod pack not found with ID: " + packId));

        try {
            RuntimeState runtimeState = containerService.stopContainer(modPack);
            modPack.setContainerId(runtimeState.containerId());
            modPack.setContainerName(runtimeState.containerName());
            modPack.setIsDeployed(true);
            modPack.setStatus("stopped");
            modPack.setUpdatedAt(Instant.now());

            ModPack savedModPack = modPackRepository.save(modPack);
            logger.info("Stop succeeded for modpack packId={}, containerId='{}'", savedModPack.getPackId(), savedModPack.getContainerId());
            return new ModPackDeployResponseDto(savedModPack, "Mod pack stopped successfully.");
        } catch (Exception e) {
            modPack.setStatus("error");
            modPack.setUpdatedAt(Instant.now());
            modPackRepository.save(modPack);
            logger.error("Stop failed for modpack packId={}", packId, e);
            throw new RuntimeException("Failed to stop mod pack with ID: " + packId, e);
        }
    }

    public void deletePack(Long packId) {
        logger.info("Deleting modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new RuntimeException("Mod pack not found with ID: " + packId));

        try {
            containerService.deleteContainer(modPack);
            fileService.deletePackDirectory(modPack);
            modPackRepository.delete(modPack);
            logger.info("Deleted modpack packId={} and associated resources.", packId);
        } catch (Exception e) {
            logger.error("Delete failed for modpack packId={}", packId, e);
            throw new RuntimeException("Failed to delete mod pack with ID: " + packId, e);
        }
    }

    public ModPackDeployResponseDto archivePack(Long packId) {
        logger.info("Archiving deployed runtime for modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new RuntimeException("Mod pack not found with ID: " + packId));

        try {
            containerService.deleteContainer(modPack);
            modPack.setContainerId(null);
            modPack.setContainerName(null);
            modPack.setIsDeployed(false);
            modPack.setStatus("not_deployed");
            modPack.setUpdatedAt(Instant.now());

            ModPack savedModPack = modPackRepository.save(modPack);
            logger.info("Archived runtime for modpack packId={}. Files retained at '{}'.", savedModPack.getPackId(), savedModPack.getPath());
            return new ModPackDeployResponseDto(savedModPack, "Mod pack archived. Container removed and files retained.");
        } catch (Exception e) {
            modPack.setStatus("error");
            modPack.setUpdatedAt(Instant.now());
            modPackRepository.save(modPack);
            logger.error("Archive failed for modpack packId={}", packId, e);
            throw new RuntimeException("Failed to archive mod pack with ID: " + packId, e);
        }
    }

    public String getPackLogs(Long packId, Integer tailLines) {
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new RuntimeException("Mod pack not found with ID: " + packId));

        int effectiveTail = tailLines == null ? 200 : tailLines;
        return containerService.readContainerLogs(modPack, effectiveTail);
    }

    private void removeMissingPackEntries() {
        List<ModPack> allPacks = modPackRepository.findAll();
        List<ModPack> stalePacks = allPacks.stream()
                .filter(pack -> !fileService.packDirectoryExists(pack))
                .toList();

        if (stalePacks.isEmpty()) {
            return;
        }

        modPackRepository.deleteAll(stalePacks);
        for (ModPack stalePack : stalePacks) {
            logger.warn(
                    "Removed stale modpack database entry packId={} because path no longer exists: {}",
                    stalePack.getPackId(),
                    stalePack.getPath()
            );
        }
    }

    public ModPackUploadResponseDto updatePack(Long packId, MultipartFile file) {
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new RuntimeException("Mod pack not found with ID: " + packId));
        if (modPack.getIsDeployed() == true) {
            stopPack(packId);
        }

        try {
            stopPack(packId);
            archivePack(packId);

            ModPackUploadResponseDto savePackResponse = savePack(file);
            Optional<ModPack> optNewPack = fileService.findModPackById(savePackResponse.packId());

            if (optNewPack.isEmpty()) {
                logger.error("Failed to find newly saved mod pack with ID: {} after saving. This should not happen.", savePackResponse.packId());
                throw new RuntimeException("Failed to find newly saved mod pack with ID: " + savePackResponse.packId());
            }
            ModPack newPack = optNewPack.get(); // unpack

            fileService.copyAndOverwriteFileFromTo("world", modPack.getPath(), newPack.getPath());
            fileService.copyAndOverwriteFileFromTo("server.properties", modPack.getPath(), newPack.getPath());
            fileService.copyAndOverwriteFileFromTo("whitelist.txt", modPack.getPath(), newPack.getPath());

            deployPack(newPack.getPackId());

            return new ModPackUploadResponseDto(modPack, UPLOAD_SUCCESS_MESSAGE);

        } catch (Exception e) {
            logger.error("Update failed for modpack packId={}", packId, e);
            return new ModPackUploadResponseDto(e.getMessage());
        }
    }

    private void applyMetadataUpdate(ModPack modPack, ModPackMetadataRequestDto metadataRequest) {
        //TODO: this should be handled in the modpack class not here
        Objects.requireNonNull(metadataRequest, "metadataRequest must not be null");

        if (StringUtils.hasText(metadataRequest.name())) {
            modPack.setName(metadataRequest.name());
        }
        if (StringUtils.hasText(metadataRequest.packVersion())) {
            modPack.setPackVersion(metadataRequest.packVersion());
        }
        if (StringUtils.hasText(metadataRequest.minecraftVersion())) {
            modPack.setMinecraftVersion(metadataRequest.minecraftVersion());
        }
        if (metadataRequest.javaVersion() != null) {
            modPack.setJavaVersion(metadataRequest.javaVersion());
        }
        if (StringUtils.hasText(metadataRequest.javaXmx())) {
            modPack.setJavaXmx(metadataRequest.javaXmx().trim());
        }
        if (StringUtils.hasText(metadataRequest.port())) {
            modPack.setPort(metadataRequest.port());
        }
        if (StringUtils.hasText(metadataRequest.entryPoint())) {
            modPack.setEntryPoint(metadataRequest.entryPoint());
        }
    }

}
