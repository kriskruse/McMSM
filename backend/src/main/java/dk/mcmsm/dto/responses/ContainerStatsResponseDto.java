package dk.mcmsm.dto.responses;

import java.time.Instant;

public record ContainerStatsResponseDto(
        Long packId,
        double cpuPercent,
        long memoryUsedBytes,
        long memoryLimitBytes,
        double memoryPercent,
        Instant timestamp
) {
}
