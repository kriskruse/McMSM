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
}

