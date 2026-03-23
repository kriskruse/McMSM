package dk.broegger_kruse.mcmsm.dto.responses;

public record LoginResponseDto(
        boolean success,
        String message,
        String username
){}


