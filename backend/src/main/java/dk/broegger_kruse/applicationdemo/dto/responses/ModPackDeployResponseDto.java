package dk.broegger_kruse.applicationdemo.dto.responses;

public record ModPackDeployResponseDto(
        Long packId,
        String name,
        String containerId,
        String containerName,
        String image,
        Integer memoryLimitMiB,
        String status,
        String message
) {
}

