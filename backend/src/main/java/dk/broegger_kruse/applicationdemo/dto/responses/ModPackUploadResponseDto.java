package dk.broegger_kruse.applicationdemo.dto.responses;

public record ModPackUploadResponseDto(
        Long packId,
        String name,
        String path,
        String packVersion,
        String minecraftVersion,
        Integer javaVersion,
        String javaXmx,
        String port,
        String entryPoint,
        String message
) {
    public ModPackUploadResponseDto(String message){
        this(null,
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

