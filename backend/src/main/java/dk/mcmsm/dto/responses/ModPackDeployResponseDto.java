package dk.mcmsm.dto.responses;

import dk.mcmsm.entities.ModPack;
import dk.mcmsm.entities.PackStatus;

public record ModPackDeployResponseDto(
        Long packId,
        String name,
        String containerId,
        String containerName,
        String image,
        Integer memoryLimitMiB,
        Integer memoryReservationMiB,
        PackStatus status,
        String message
) {
    public ModPackDeployResponseDto(Long packId, PackStatus status, String message) {
        this(
                packId,
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                message
        );
    }

    public ModPackDeployResponseDto(ModPack modPack, String message) {
        this(
                modPack.getPackId(),
                modPack.getName(),
                modPack.getContainerId(),
                modPack.getContainerName(),
                null,
                null,
                null,
                modPack.getStatus(),
                message
        );
    }
}

