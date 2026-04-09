package dk.mcmsm.dto.responses;

public record LoginResponseDto(
        boolean success,
        String message,
        String username
){}


