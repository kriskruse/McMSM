package dk.mcmsm.dto.responses;

import dk.mcmsm.entities.ModPack;

import java.util.Objects;

import static dk.mcmsm.services.ModPackFileService.DEFAULT_JAVA_XMX;

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
        String[] entryPointCandidates,
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
                null,
                message
        );
    }
    public ModPackUploadResponseDto(ModPack modPack, String message){
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
                message
        );
    }
}

