package dk.mcmsm.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import dk.mcmsm.dto.responses.ContainerStatsResponseDto;
import dk.mcmsm.entities.ModPack;
import dk.mcmsm.exception.ModPackNotFoundException;
import dk.mcmsm.repository.ModPackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads live resource usage for deployed modpack containers.
 */
@Service
public class ContainerStatsService {

    private static final Logger logger = LoggerFactory.getLogger(ContainerStatsService.class);
    private static final long STATS_TIMEOUT_SECONDS = 3L;

    private final DockerClient dockerClient;
    private final ContainerService containerService;
    private final ModPackRepository modPackRepository;

    public ContainerStatsService(DockerClient dockerClient,
                                 ContainerService containerService,
                                 ModPackRepository modPackRepository) {
        this.dockerClient = dockerClient;
        this.containerService = containerService;
        this.modPackRepository = modPackRepository;
    }

    /**
     * Fetches a single stats snapshot for a deployed, running container.
     *
     * @param packId the modpack ID
     * @return stats snapshot, or empty if the container is not running
     * @throws ModPackNotFoundException if the modpack does not exist
     */
    public Optional<ContainerStatsResponseDto> getStats(Long packId) {
        ModPack modPack = modPackRepository.findByPackId(packId)
                .orElseThrow(() -> new ModPackNotFoundException(packId));

        var containerId = containerService.resolveRunningContainerId(modPack);
        if (containerId == null) {
            return Optional.empty();
        }

        var stats = fetchStatsFrame(containerId);
        if (stats == null) {
            return Optional.empty();
        }

        var cpuPercent = computeCpuPercent(stats);
        var memUsed = computeMemoryUsed(stats);
        var memLimit = computeMemoryLimit(stats);
        var memPercent = memLimit > 0 ? (memUsed * 100.0 / memLimit) : 0.0;

        return Optional.of(new ContainerStatsResponseDto(
                packId,
                cpuPercent,
                memUsed,
                memLimit,
                memPercent,
                Instant.now()
        ));
    }

    private Statistics fetchStatsFrame(String containerId) {
        var ref = new AtomicReference<Statistics>();
        var latch = new CountDownLatch(1);

        try (var callback = new ResultCallback.Adapter<Statistics>() {
            @Override
            public void onNext(Statistics item) {
                if (item != null && ref.compareAndSet(null, item)) {
                    latch.countDown();
                }
                super.onNext(item);
            }
        }) {
            dockerClient.statsCmd(containerId).withNoStream(true).exec(callback);
            if (!latch.await(STATS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Timed out fetching stats for container '{}'.", containerId);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.warn("Failed to fetch stats for container '{}': {}", containerId, e.getMessage());
            return null;
        }

        return ref.get();
    }

    private double computeCpuPercent(Statistics stats) {
        var cpu = stats.getCpuStats();
        var preCpu = stats.getPreCpuStats();
        if (cpu == null || preCpu == null
                || cpu.getCpuUsage() == null || preCpu.getCpuUsage() == null
                || cpu.getCpuUsage().getTotalUsage() == null
                || preCpu.getCpuUsage().getTotalUsage() == null
                || cpu.getSystemCpuUsage() == null
                || preCpu.getSystemCpuUsage() == null) {
            return 0.0;
        }

        long cpuDelta = cpu.getCpuUsage().getTotalUsage() - preCpu.getCpuUsage().getTotalUsage();
        long systemDelta = cpu.getSystemCpuUsage() - preCpu.getSystemCpuUsage();
        if (cpuDelta <= 0 || systemDelta <= 0) {
            return 0.0;
        }

        long onlineCpus = cpu.getOnlineCpus() != null
                ? cpu.getOnlineCpus()
                : (cpu.getCpuUsage().getPercpuUsage() == null ? 1 : cpu.getCpuUsage().getPercpuUsage().size());
        if (onlineCpus <= 0) {
            onlineCpus = 1;
        }

        return (cpuDelta * 1.0 / systemDelta) * onlineCpus * 100.0;
    }

    private long computeMemoryUsed(Statistics stats) {
        var mem = stats.getMemoryStats();
        if (mem == null || mem.getUsage() == null) {
            return 0L;
        }
        return mem.getUsage();
    }

    private long computeMemoryLimit(Statistics stats) {
        var mem = stats.getMemoryStats();
        if (mem == null || mem.getLimit() == null) {
            return 0L;
        }
        return mem.getLimit();
    }
}
