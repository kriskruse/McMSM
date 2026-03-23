package dk.broegger_kruse.mcmsm.dto.requests;

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

