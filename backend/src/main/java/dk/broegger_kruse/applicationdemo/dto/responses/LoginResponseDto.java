package dk.broegger_kruse.applicationdemo.dto.responses;

public record LoginResponseDto(
        boolean success,
        String message,
        String username
){}


