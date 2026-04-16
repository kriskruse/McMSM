package dk.mcmsm.e2e;

import dk.mcmsm.controller.GlobalExceptionHandler.ErrorResponse;
import dk.mcmsm.entities.ModPack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class ModPackCommandAndLogsE2ETest extends BaseE2ETest {

    @Test
    void getLogs_returnsContainerLogs() {
        var result = uploadAndDeploy();
        stubLogs("Server started on port 25565\nDone (5.2s)!");

        var response = get("/api/modpacks/" + result.upload().packId() + "/logs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Server started");
        assertThat(response.getBody()).contains("Done");
    }

    @Test
    void getLogs_respectsTailParameter() {
        var result = uploadAndDeploy();
        stubLogs("some log line");

        get("/api/modpacks/" + result.upload().packId() + "/logs?tail=50", String.class);

        var tailCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(containerLogService).readContainerLogs(any(ModPack.class), tailCaptor.capture());
        assertThat(tailCaptor.getValue()).isEqualTo(50);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendCommand_returnsConfirmation() {
        var result = uploadAndDeploy();
        stubCommand();

        var response = post(
                "/api/modpacks/" + result.upload().packId() + "/command",
                Map.of("command", "say hello"),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Command sent.");
        verify(containerLogService).executeCommand(any(ModPack.class), eq("say hello"));
    }

    @Test
    void getLogs_nonExistentPack_returns404() {
        var response = get("/api/modpacks/99999/logs", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("99999");
    }
}
