package dk.mcmsm.controller;

import dk.mcmsm.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthCheckController {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);
    private final ContainerService containerService;

    public HealthCheckController(ContainerService containerService) {
        this.containerService = containerService;
    }


    @GetMapping("")
    public ResponseEntity<String> healthCheck() {
        logger.debug("Health check endpoint invoked.");
        return ResponseEntity.ok("Healthy");
    }

    @GetMapping("/docker")
    public ResponseEntity<String> dockerHealthCheck() {
        logger.debug("Docker health check endpoint invoked.");
        return containerService.isDockerRunning()
                ? ResponseEntity.ok("Docker is running")
                : ResponseEntity.status(503).body("Docker is not reachable");
    }
}
