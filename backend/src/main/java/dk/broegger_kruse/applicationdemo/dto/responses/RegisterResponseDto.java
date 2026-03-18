package dk.broegger_kruse.applicationdemo.dto.responses;

public record RegisterResponseDto(
        boolean success,
        String message,
        String username
){}


