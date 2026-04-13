package dk.mcmsm.dto.responses;

import dk.mcmsm.entities.ModPack;
import dk.mcmsm.entities.PackStatus;

import java.util.Objects;

import static dk.mcmsm.services.ModPackFileService.DEFAULT_JAVA_XMX;

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
        PackStatus status,
        String loaderType,
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
                null,
                message
        );
    }

    public ModPackMetadataResponseDto(ModPack modPack, String message) {
        this(
                modPack.getPackId(),
                modPack.getName(),
                modPack.getPath(),
                modPack.getPackVersion(),
                modPack.getMinecraftVersion(),
                modPack.getJavaVersion(),
                Objects.requireNonNullElse(modPack.getJavaXmx(), DEFAULT_JAVA_XMX),
                modPack.getPort(),
                modPack.getEntryPoint(),
                modPack.getEntryPointCandidates(),
                modPack.getIsDeployed(),
                modPack.getStatus(),
                modPack.getLoaderType(),
                message
        );
    }
}
