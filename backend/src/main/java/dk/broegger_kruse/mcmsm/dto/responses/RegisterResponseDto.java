package dk.broegger_kruse.mcmsm.dto.responses;

public record RegisterResponseDto(
        boolean success,
        String message,
        String username
){}


