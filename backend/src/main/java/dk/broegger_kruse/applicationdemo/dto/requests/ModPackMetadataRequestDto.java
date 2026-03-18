package dk.broegger_kruse.applicationdemo.dto.requests;

public record ModPackMetadataRequestDto(
        String name,
        String packVersion,
        String minecraftVersion,
        Integer javaVersion,
        String javaXmx,
        String port,
        String entryPoint
) {
}

