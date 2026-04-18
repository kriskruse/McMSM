package dk.mcmsm.controller;

import dk.mcmsm.dto.responses.SystemStatsResponseDto;
import dk.mcmsm.services.SystemStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing host-level system metrics.
 */
@RestController
@RequestMapping("/api/system")
public class SystemStatsController {

    private final SystemStatsService systemStatsService;

    public SystemStatsController(SystemStatsService systemStatsService) {
        this.systemStatsService = systemStatsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<SystemStatsResponseDto> getStats() {
        return ResponseEntity.ok(systemStatsService.getStats());
    }
}
