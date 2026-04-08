package dk.broegger_kruse.mcmsm.dto.responses;

import dk.broegger_kruse.mcmsm.entities.ModPack;

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
    public ModPackDeployResponseDto(Long packId, String status , String message){
        this(
                packId,
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
                modPack.getStatus(),
                message
        );
    }
}

