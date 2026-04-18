package dk.mcmsm.services;

import dk.mcmsm.dto.responses.SystemStatsResponseDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reads host-level CPU, memory, and disk statistics via OSHI.
 */
@Service
public class SystemStatsService {

    private static final Logger logger = LoggerFactory.getLogger(SystemStatsService.class);
    private static final long MIN_CPU_SAMPLE_GAP_MS = 500L;

    private final String modpacksRoot;
    private final SystemInfo systemInfo = new SystemInfo();
    private final ReentrantLock cpuLock = new ReentrantLock();

    private CentralProcessor processor;
    private HardwareAbstractionLayer hardware;
    private long[] prevTicks = new long[0];
    private Instant prevTicksAt = Instant.EPOCH;
    private double lastCpuPercent = 0.0;

    public SystemStatsService(@Value("${app.storage.modpacks-root}") String modpacksRoot) {
        this.modpacksRoot = modpacksRoot;
    }

    @PostConstruct
    void init() {
        hardware = systemInfo.getHardware();
        processor = hardware.getProcessor();
        prevTicks = processor.getSystemCpuLoadTicks();
        prevTicksAt = Instant.now();
    }

    public SystemStatsResponseDto getStats() {
        var cpuPercent = sampleCpu();
        GlobalMemory mem = hardware.getMemory();
        long memTotal = mem.getTotal();
        long memAvail = mem.getAvailable();
        long memUsed = Math.max(0L, memTotal - memAvail);
        double memPercent = memTotal > 0 ? (memUsed * 100.0 / memTotal) : 0.0;

        long diskTotal = 0L;
        long diskUsed = 0L;
        double diskPercent = 0.0;
        try {
            FileStore store = resolveFileStore();
            if (store != null) {
                diskTotal = store.getTotalSpace();
                long usable = store.getUsableSpace();
                diskUsed = Math.max(0L, diskTotal - usable);
                diskPercent = diskTotal > 0 ? (diskUsed * 100.0 / diskTotal) : 0.0;
            }
        } catch (IOException e) {
            logger.warn("Failed to read disk stats for '{}': {}", modpacksRoot, e.getMessage());
        }

        return new SystemStatsResponseDto(
                cpuPercent,
                memUsed,
                memTotal,
                memPercent,
                diskUsed,
                diskTotal,
                diskPercent,
                Instant.now()
        );
    }

    private double sampleCpu() {
        cpuLock.lock();
        try {
            var now = Instant.now();
            if (java.time.Duration.between(prevTicksAt, now).toMillis() < MIN_CPU_SAMPLE_GAP_MS) {
                return lastCpuPercent;
            }
            double load = processor.getSystemCpuLoadBetweenTicks(prevTicks);
            prevTicks = processor.getSystemCpuLoadTicks();
            prevTicksAt = now;
            lastCpuPercent = load * 100.0;
            return lastCpuPercent;
        } finally {
            cpuLock.unlock();
        }
    }

    private FileStore resolveFileStore() throws IOException {
        var path = Path.of(modpacksRoot).toAbsolutePath();
        var probe = path;
        while (probe != null && !Files.exists(probe)) {
            probe = probe.getParent();
        }
        if (probe == null) {
            return null;
        }
        return Files.getFileStore(probe);
    }
}
