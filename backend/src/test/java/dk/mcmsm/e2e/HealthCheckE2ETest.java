package dk.mcmsm.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class HealthCheckE2ETest extends BaseE2ETest {

    @Test
    void healthCheck_returnsHealthy() {
        var response = get("/api/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Healthy");
    }

    @Test
    void dockerHealth_running_returns200() {
        when(containerService.isDockerRunning()).thenReturn(true);

        var response = get("/api/health/docker", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Docker is running");
    }

    @Test
    void dockerHealth_notRunning_returns503() {
        when(containerService.isDockerRunning()).thenReturn(false);

        var response = get("/api/health/docker", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("Docker is not reachable");
    }
}
