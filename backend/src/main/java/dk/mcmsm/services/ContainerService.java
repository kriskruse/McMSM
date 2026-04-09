package dk.mcmsm.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import dk.mcmsm.entities.ModPack;
import dk.mcmsm.entities.PackStatus;
import dk.mcmsm.repository.ModPackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Service
public class ContainerService {
    private static final Logger logger = LoggerFactory.getLogger(ContainerService.class);
    private static final String CONTAINER_WORKDIR = "/server";
    private static final String DEPLOY_PREFIX = "modpack-";

    private final DockerClient dockerClient;
    private final ModPackRepository modPackRepository;

    public ContainerService(ModPackRepository modPackRepository) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        this.modPackRepository = modPackRepository;
    }

    @Scheduled(fixedDelayString = "${app.runtime-sync.interval-ms:15000}")
    public void syncDeployedModpackRuntimeStates() {
        var deployedPacks = modPackRepository.getAllByIsDeployed(true);
        logger.debug("Runtime sync started for {} deployed modpacks.", deployedPacks.size());
        for (var modPack : deployedPacks) {
            var runtimeState = inspectRuntimeState(modPack);
            var changed = runtimeState == null
                    ? applyNotDeployedState(modPack)
                    : applyRuntimeState(modPack, runtimeState);

            if (changed) {
                modPack.setUpdatedAt(Instant.now());
                modPackRepository.save(modPack);
                logger.info("Runtime sync updated packId={} to status='{}' (containerId='{}').", modPack.getPackId(), modPack.getStatus(), modPack.getContainerId());
            }
        }
    }

    public DeploymentResult deployServer(ModPack modPack, int memoryLimitMiB) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        logger.info("Deploying container for packId={}, name='{}', requestedPort='{}', memoryLimitMiB={}", modPack.getPackId(), modPack.getName(), modPack.getPort(), memoryLimitMiB);

        var packPath = Path.of(modPack.getPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(packPath)) {
            throw new IllegalStateException("Modpack directory does not exist: " + packPath);
        }

        var hostPort = parseHostPort(modPack.getPort());
        var containerName = buildContainerName(modPack);
        var image = resolveJavaImage(modPack.getJavaVersion());

        pullImageIfNeeded(image);
        removeContainerIfExists(containerName);

        var containerPort = ExposedPort.tcp(hostPort);
        var hostConfig = HostConfig.newHostConfig()
                .withMemory((long) memoryLimitMiB * 1024 * 1024)
                .withBinds(new Bind(packPath.toString(), new Volume(CONTAINER_WORKDIR)))
                .withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort), containerPort));

        var containerResponse = dockerClient.createContainerCmd(image)
                .withName(containerName)
                .withWorkingDir(CONTAINER_WORKDIR)
                .withHostConfig(hostConfig)
                .withExposedPorts(containerPort)
                .withCmd("bash", CONTAINER_WORKDIR + "/" + modPack.getEntryPoint())
                .exec();

        dockerClient.startContainerCmd(containerResponse.getId()).exec();
        logger.info("Container started for packId={} with containerId='{}' and name='{}'.", modPack.getPackId(), containerResponse.getId(), containerName);
        return new DeploymentResult(containerResponse, containerName, image, memoryLimitMiB);
    }

    public void deleteContainer(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");

        var containerRef = resolveContainerReference(modPack);
        if (containerRef == null) {
            logger.debug("No container found to delete for packId={}", modPack.getPackId());
            return;
        }

        dockerClient.removeContainerCmd(containerRef.containerId()).withForce(true).exec();
        logger.info("Deleted container for packId={} with containerId='{}'.", modPack.getPackId(), containerRef.containerId());
    }

    public RuntimeState startContainer(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        logger.info("Starting container for packId={}", modPack.getPackId());

        var containerRef = resolveContainerReference(modPack);
        if (containerRef == null) {
            throw new IllegalStateException("No container found for mod pack " + modPack.getPackId() + ".");
        }

        if (!containerRef.running()) {
            dockerClient.startContainerCmd(containerRef.containerId()).exec();
            logger.info("Container started for packId={} with containerId='{}'.", modPack.getPackId(), containerRef.containerId());
        } else {
            logger.debug("Container already running for packId={} with containerId='{}'.", modPack.getPackId(), containerRef.containerId());
        }

        return new RuntimeState(containerRef.containerId(), containerRef.containerName(), true);
    }

    public RuntimeState stopContainer(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        logger.info("Stopping container for packId={}", modPack.getPackId());

        var containerRef = resolveContainerReference(modPack);
        if (containerRef == null) {
            throw new IllegalStateException("No container found for mod pack " + modPack.getPackId() + ".");
        }

        if (containerRef.running()) {
            dockerClient.stopContainerCmd(containerRef.containerId()).exec();
            logger.info("Container stopped for packId={} with containerId='{}'.", modPack.getPackId(), containerRef.containerId());
        } else {
            logger.debug("Container already stopped for packId={} with containerId='{}'.", modPack.getPackId(), containerRef.containerId());
        }

        return new RuntimeState(containerRef.containerId(), containerRef.containerName(), false);
    }

    public RuntimeState inspectRuntimeState(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");

        var containerRef = resolveContainerReference(modPack);
        if (containerRef == null) {
            logger.debug("No runtime state found for packId={}", modPack.getPackId());
            return null;
        }

        return new RuntimeState(containerRef.containerId(), containerRef.containerName(), containerRef.running());
    }

    public String readContainerLogs(ModPack modPack, int tailLines) {
        Objects.requireNonNull(modPack, "modPack must not be null");

        var containerRef = resolveContainerReference(modPack);
        if (containerRef == null) {
            return "No container found for modpack " + modPack.getPackId() + ".";
        }

        var logsBuilder = new StringBuilder();
        try (var callback = new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame item) {
                if (item != null && item.getPayload() != null) {
                    logsBuilder.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                }
                super.onNext(item);
            }
        }) {
            dockerClient.logContainerCmd(containerRef.containerId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(Math.max(1, tailLines))
                    .exec(callback);
            callback.awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading logs for mod pack " + modPack.getPackId() + ".", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close log stream for mod pack " + modPack.getPackId() + ".", e);
        }

        return logsBuilder.toString().trim();
    }

    private ContainerReference resolveContainerReference(ModPack modPack) {
        if (hasText(modPack.getContainerId())) {
            try {
                var inspect = dockerClient.inspectContainerCmd(modPack.getContainerId()).exec();
                var containerName = inspect.getName() == null
                        ? buildContainerName(modPack)
                        : inspect.getName().replaceFirst("^/", "");
                var running = inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning());
                return new ContainerReference(modPack.getContainerId(), containerName, running);
            } catch (NotFoundException ignored) {
                // Fall back to name lookup.
                logger.debug("ContainerId lookup failed for packId={} and containerId='{}', falling back to name lookup.", modPack.getPackId(), modPack.getContainerId());
            }
        }

        var expectedName = "/" + buildContainerName(modPack);
        var containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        for (var container : containers) {
            var names = container.getNames();
            if (names == null) {
                continue;
            }

            for (var name : names) {
                if (expectedName.equals(name)) {
                    return new ContainerReference(
                            container.getId(),
                            name.replaceFirst("^/", ""),
                            "running".equalsIgnoreCase(container.getState())
                    );
                }
            }
        }

        return null;
    }

    private void pullImageIfNeeded(String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
            logger.debug("Docker image already available locally: {}", image);
            return;
        } catch (NotFoundException ignored) {
            // Pull when image is not available locally.
            logger.info("Pulling docker image {}.", image);
        }

        try {
            dockerClient.pullImageCmd(image).start().awaitCompletion();
            logger.info("Docker image pull completed: {}", image);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pulling image " + image + ".", e);
        }
    }

    private void removeContainerIfExists(String containerName) {
        var containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        for (var container : containers) {
            var names = container.getNames();
            if (names == null) {
                continue;
            }

            for (var name : names) {
                if (("/" + containerName).equals(name)) {
                    dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                    logger.info("Removed existing container '{}' with id='{}' before deployment.", containerName, container.getId());
                    return;
                }
            }
        }
    }

    private String resolveJavaImage(Integer javaVersion) {
        var effectiveVersion = javaVersion == null ? 21 : javaVersion;
        return "eclipse-temurin:" + effectiveVersion + "-jre";
    }

    private String buildContainerName(ModPack modPack) {
        var idPart = modPack.getPackId() == null ? "draft" : modPack.getPackId().toString();
        var safeName = Objects.requireNonNullElse(modPack.getName(), "modpack")
                .toLowerCase()
                .replaceAll("[^a-z0-9_.-]", "-");
        return DEPLOY_PREFIX + idPart + "-" + safeName;
    }

    private int parseHostPort(String port) {
        try {
            var parsed = Integer.parseInt(Objects.requireNonNullElse(port, "25565").trim());
            if (parsed < 1 || parsed > 65535) {
                throw new IllegalStateException("Port out of range: " + port);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid port value: " + port, e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean applyNotDeployedState(ModPack modPack) {
        var changed = false;

        if (modPack.getStatus() != PackStatus.NOT_DEPLOYED) {
            modPack.setStatus(PackStatus.NOT_DEPLOYED);
            changed = true;
        }
        if (Boolean.TRUE.equals(modPack.getIsDeployed())) {
            modPack.setIsDeployed(false);
            changed = true;
        }
        if (modPack.getContainerId() != null) {
            modPack.setContainerId(null);
            changed = true;
        }
        if (modPack.getContainerName() != null) {
            modPack.setContainerName(null);
            changed = true;
        }

        return changed;
    }

    private boolean applyRuntimeState(ModPack modPack, RuntimeState runtimeState) {
        var changed = false;
        var expectedStatus = runtimeState.running() ? PackStatus.RUNNING : PackStatus.STOPPED;

        if (expectedStatus != modPack.getStatus()) {
            modPack.setStatus(expectedStatus);
            changed = true;
        }
        if (!Boolean.TRUE.equals(modPack.getIsDeployed())) {
            modPack.setIsDeployed(true);
            changed = true;
        }
        if (!Objects.equals(modPack.getContainerId(), runtimeState.containerId())) {
            modPack.setContainerId(runtimeState.containerId());
            changed = true;
        }
        if (!Objects.equals(modPack.getContainerName(), runtimeState.containerName())) {
            modPack.setContainerName(runtimeState.containerName());
            changed = true;
        }

        return changed;
    }

    public record DeploymentResult(
            String containerId,
            String containerName,
            String image,
            Integer memoryLimitMiB
    ) {
        DeploymentResult(CreateContainerResponse containerResponse, String containerName, String image, Integer memoryLimitMiB) {
            this(containerResponse.getId(), containerName, image, memoryLimitMiB);
        }
    }

    private record ContainerReference(
            String containerId,
            String containerName,
            boolean running
    ) {
    }

    public record RuntimeState(
            String containerId,
            String containerName,
            boolean running
    ) {
    }

}
