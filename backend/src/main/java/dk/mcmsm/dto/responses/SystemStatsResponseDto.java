package dk.mcmsm.dto.responses;

import java.time.Instant;

public record SystemStatsResponseDto(
        double cpuPercent,
        long memoryUsedBytes,
        long memoryTotalBytes,
        double memoryPercent,
        long diskUsedBytes,
        long diskTotalBytes,
        double diskPercent,
        Instant timestamp
) {
}
