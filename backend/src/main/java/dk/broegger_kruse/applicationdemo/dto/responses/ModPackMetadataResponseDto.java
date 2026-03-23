package dk.broegger_kruse.applicationdemo.dto.responses;

public record ModPackMetadataResponseDto(
        Long packId,
        String name,
        String path,
        String packVersion,
        String minecraftVersion,
        Integer javaVersion,
        String javaXmx,
        String port,
        String entryPoint,
        String[] entryPointCandidates,
        Boolean isDeployed,
        String status,
        String message
) {
    public ModPackMetadataResponseDto(Long packId, String message) {
        this(packId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                message
        );
    }
}

