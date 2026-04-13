package dk.mcmsm.services;

import dk.mcmsm.dto.requests.ModPackMetadataRequestDto;
import dk.mcmsm.dto.responses.ModPackDeployResponseDto;
import dk.mcmsm.dto.responses.ModPackMetadataResponseDto;
import dk.mcmsm.dto.responses.ModPackUploadResponseDto;
import dk.mcmsm.entities.ModPack;
import dk.mcmsm.entities.PackStatus;
import dk.mcmsm.exception.ModPackNotFoundException;
import dk.mcmsm.exception.ModPackOperationException;
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

/**
 * Core business logic for modpack lifecycle operations.
 */
@Service
public class McModPackService {
    private static final Logger logger = LoggerFactory.getLogger(McModPackService.class);
    private static final String DEFAULT_JAVA_XMX = "5G";
    private static final String UPLOAD_SUCCESS_MESSAGE = "Mod pack uploaded. Review metadata and submit corrections if needed.";
    private static final String METADATA_SUCCESS_MESSAGE = "Metadata updated successfully.";

    /** Server-generated files and directories that must survive a modpack update. */
    private static final List<String> PERSISTENT_SERVER_DATA = List.of(
            "world",
            "server.properties",
            "whitelist.json",
            "whitelist.txt",
            "ops.json",
            "banned-players.json",
            "banned-ips.json",
            "server-icon.png"
    );

    private final ContainerService containerService;
    private final ModPackRepository modPackRepository;
    private final ModPackFileService fileService;

    public McModPackService(ContainerService containerService, ModPackRepository modPackRepository, ModPackFileService fileService) {
        this.containerService = containerService;
        this.modPackRepository = modPackRepository;
        this.fileService = fileService;
    }

    /**
     * Returns all modpacks after pruning stale entries.
     *
     * @return list of all modpacks
     */
    public List<ModPack> getAllPacks() {
        removeMissingPackEntries();
        List<ModPack> packs = modPackRepository.findAll();
        logger.debug("Loaded {} modpacks from repository.", packs.size());
        return packs;
    }

    /**
     * Returns modpacks that are saved but not deployed.
     *
     * @return list of non-deployed modpacks
     */
    public List<ModPack> getSavedPacks() {
        removeMissingPackEntries();
        List<ModPack> packs = modPackRepository.getAllByIsDeployed(false);
        logger.debug("Loaded {} saved (not deployed) modpacks.", packs.size());
        return packs;
    }

    /**
     * Returns modpacks that are currently deployed.
     *
     * @return list of deployed modpacks
     */
    public List<ModPack> getDeployedPacks() {
        removeMissingPackEntries();
        List<ModPack> packs = modPackRepository.getAllByIsDeployed(true);
        logger.debug("Loaded {} deployed modpacks.", packs.size());
        return packs;
    }

    /**
     * Processes an uploaded modpack archive: extracts, saves metadata, and assigns storage.
     *
     * @param file the uploaded archive
     * @return upload response with inferred metadata
     */
    public ModPackUploadResponseDto savePack(MultipartFile file) {
        logger.info("Saving uploaded modpack file='{}' ({} bytes).", file.getOriginalFilename(), file.getSize());
        ModPack createdModPack = fileService.createDraftModPackFromFile(file);
        ModPack savedModPack = modPackRepository.save(createdModPack);
        fileService.assignImmutablePackDirectoryPath(savedModPack);
        savedModPack = modPackRepository.save(savedModPack);
        logger.info("Saved modpack packId={}, name='{}', path='{}'.", savedModPack.getPackId(), savedModPack.getName(), savedModPack.getPath());
        return new ModPackUploadResponseDto(savedModPack, UPLOAD_SUCCESS_MESSAGE);
    }

    /**
     * Updates modpack metadata fields.
     *
     * @param packId          the modpack ID
     * @param metadataRequest the new metadata values
     * @return updated metadata response
     * @throws ModPackNotFoundException if the modpack does not exist
     */
    public ModPackMetadataResponseDto updateMetadata(Long packId, ModPackMetadataRequestDto metadataRequest) {
        logger.info("Updating metadata for modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

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

    /**
     * Deploys a modpack to a Docker container.
     *
     * @param packId the modpack ID
     * @return deployment response with container details
     * @throws ModPackNotFoundException   if the modpack does not exist
     * @throws ModPackOperationException if the deployment fails
     */
    public ModPackDeployResponseDto deployPack(Long packId) {
        logger.info("Deploying modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

        try {
            fileService.syncServerPortWithMetadata(modPack);
            int memoryLimitMiB = fileService.resolveContainerMemoryLimitMiB(modPack);
            DeploymentResult deploymentResult = containerService.deployServer(modPack, memoryLimitMiB);

            modPack.setContainerId(deploymentResult.containerId());
            modPack.setContainerName(deploymentResult.containerName());
            modPack.setIsDeployed(true);
            modPack.setStatus(PackStatus.RUNNING);
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
            modPack.setStatus(PackStatus.DEPLOY_FAILED);
            modPack.setLastDeployError(e.getMessage());
            modPack.setLastDeployAt(Instant.now());
            modPack.setUpdatedAt(Instant.now());
            modPackRepository.save(modPack);
            logger.error("Deployment failed for modpack packId={}", packId, e);
            throw new ModPackOperationException(packId, "deploy", e);
        }
    }

    /**
     * Starts a deployed modpack's Docker container.
     *
     * @param packId the modpack ID
     * @return deployment response with updated state
     * @throws ModPackNotFoundException   if the modpack does not exist
     * @throws ModPackOperationException if the start fails
     */
    public ModPackDeployResponseDto startPack(Long packId) {
        logger.info("Starting modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

        try {
            RuntimeState runtimeState = containerService.startContainer(modPack);
            modPack.setContainerId(runtimeState.containerId());
            modPack.setContainerName(runtimeState.containerName());
            modPack.setIsDeployed(true);
            modPack.setStatus(PackStatus.RUNNING);
            modPack.setUpdatedAt(Instant.now());

            ModPack savedModPack = modPackRepository.save(modPack);
            logger.info("Start succeeded for modpack packId={}, containerId='{}'", savedModPack.getPackId(), savedModPack.getContainerId());
            return new ModPackDeployResponseDto(savedModPack, "Mod pack started successfully.");
        } catch (Exception e) {
            modPack.setStatus(PackStatus.ERROR);
            modPack.setUpdatedAt(Instant.now());
            modPackRepository.save(modPack);
            logger.error("Start failed for modpack packId={}", packId, e);
            throw new ModPackOperationException(packId, "start", e);
        }
    }

    /**
     * Stops a modpack that is currently deployed and running in Docker.
     *
     * @param packId the modpack ID
     * @return deployment response with updated state
     * @throws ModPackNotFoundException   if the modpack does not exist
     * @throws ModPackOperationException if the stop fails
     */
    public ModPackDeployResponseDto stopPack(Long packId) {
        logger.info("Stopping modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

        try {
            RuntimeState runtimeState = containerService.stopContainer(modPack);
            modPack.setContainerId(runtimeState.containerId());
            modPack.setContainerName(runtimeState.containerName());
            modPack.setIsDeployed(true);
            modPack.setStatus(PackStatus.STOPPED);
            modPack.setUpdatedAt(Instant.now());

            ModPack savedModPack = modPackRepository.save(modPack);
            logger.info("Stop succeeded for modpack packId={}, containerId='{}'", savedModPack.getPackId(), savedModPack.getContainerId());
            return new ModPackDeployResponseDto(savedModPack, "Mod pack stopped successfully.");
        } catch (Exception e) {
            modPack.setStatus(PackStatus.ERROR);
            modPack.setUpdatedAt(Instant.now());
            modPackRepository.save(modPack);
            logger.error("Stop failed for modpack packId={}", packId, e);
            throw new ModPackOperationException(packId, "stop", e);
        }
    }

    /**
     * Deletes a modpack: removes the container, files, and metadata.
     *
     * @param packId the modpack ID
     * @throws ModPackNotFoundException   if the modpack does not exist
     * @throws ModPackOperationException if the deletion fails
     */
    public void deletePack(Long packId) {
        logger.info("Deleting modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

        try {
            containerService.deleteContainer(modPack);
            fileService.deletePackDirectory(modPack);
            modPackRepository.delete(modPack);
            logger.info("Deleted modpack packId={} and associated resources.", packId);
        } catch (Exception e) {
            logger.error("Delete failed for modpack packId={}", packId, e);
            throw new ModPackOperationException(packId, "delete", e);
        }
    }

    /**
     * Archives a deployed modpack: removes the container but retains files on disk.
     *
     * @param packId the modpack ID
     * @return deployment response with archived state
     * @throws ModPackNotFoundException   if the modpack does not exist
     * @throws ModPackOperationException if the archive fails
     */
    public ModPackDeployResponseDto archivePack(Long packId) {
        logger.info("Archiving deployed runtime for modpack packId={}", packId);
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

        try {
            containerService.deleteContainer(modPack);
            modPack.setContainerId(null);
            modPack.setContainerName(null);
            modPack.setIsDeployed(false);
            modPack.setStatus(PackStatus.NOT_DEPLOYED);
            modPack.setUpdatedAt(Instant.now());

            ModPack savedModPack = modPackRepository.save(modPack);
            logger.info("Archived runtime for modpack packId={}. Files retained at '{}'.", savedModPack.getPackId(), savedModPack.getPath());
            return new ModPackDeployResponseDto(savedModPack, "Mod pack archived. Container removed and files retained.");
        } catch (Exception e) {
            modPack.setStatus(PackStatus.ERROR);
            modPack.setUpdatedAt(Instant.now());
            modPackRepository.save(modPack);
            logger.error("Archive failed for modpack packId={}", packId, e);
            throw new ModPackOperationException(packId, "archive", e);
        }
    }

    /**
     * Reads Docker container logs for a modpack.
     *
     * @param packId    the modpack ID
     * @param tailLines number of trailing log lines to fetch
     * @return the container log output
     * @throws ModPackNotFoundException if the modpack does not exist
     */
    public String getPackLogs(Long packId, Integer tailLines) {
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

        int effectiveTail = tailLines == null ? 200 : tailLines;
        return containerService.readContainerLogs(modPack, effectiveTail);
    }

    /**
     * Updates a modpack with a new archive: stops the running instance, uploads new files,
     * preserves world/properties/whitelist, and redeploys.
     *
     * @param packId the modpack ID to update
     * @param file   the new modpack archive
     * @return upload response with result
     * @throws ModPackNotFoundException if the modpack does not exist
     */
    public ModPackUploadResponseDto updatePack(Long packId, MultipartFile file) {
        ModPack oldPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

        if (Boolean.TRUE.equals(oldPack.getIsDeployed())) {
            stopPack(packId);
        }

        try {
            stopPack(packId);
            archivePack(packId);

            ModPackUploadResponseDto savePackResponse = savePack(file);
            ModPack newPack = modPackRepository.findByPackId(savePackResponse.packId())
                    .orElseThrow(() -> new ModPackOperationException(packId, "update",
                            new IllegalStateException("Failed to find newly saved mod pack with ID: " + savePackResponse.packId())));

            for (String item : PERSISTENT_SERVER_DATA) {
                fileService.copyIfExists(item, oldPack.getPath(), newPack.getPath());
            }

            deployPack(newPack.getPackId());

            fileService.deletePackDirectory(oldPack);
            modPackRepository.delete(oldPack);
            logger.info("Cleaned up old modpack packId={} after successful update to packId={}.", packId, newPack.getPackId());

            return new ModPackUploadResponseDto(newPack, UPLOAD_SUCCESS_MESSAGE);
        } catch (Exception e) {
            logger.error("Update failed for modpack packId={}", packId, e);
            return new ModPackUploadResponseDto(e.getMessage());
        }
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

    private void applyMetadataUpdate(ModPack modPack, ModPackMetadataRequestDto metadataRequest) {
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
