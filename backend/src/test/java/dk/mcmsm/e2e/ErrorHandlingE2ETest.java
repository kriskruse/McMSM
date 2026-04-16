package dk.mcmsm.e2e;

import dk.mcmsm.controller.GlobalExceptionHandler.ErrorResponse;
import dk.mcmsm.entities.ModPack;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class ErrorHandlingE2ETest extends BaseE2ETest {

    @Test
    void notFound_returns404WithBody() {
        var response = post("/api/modpacks/99999/deploy", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isNotBlank();
        assertThat(response.getBody().message()).contains("99999");
    }

    @Test
    void deployFailure_returns500() {
        var upload = uploadForgePack();
        when(containerService.deployServer(any(ModPack.class), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Docker daemon unreachable"));

        var response = post("/api/modpacks/" + upload.packId() + "/deploy", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains(upload.packId().toString());
    }

    @Test
    void startFailure_returns500() {
        var result = uploadAndDeploy();
        when(containerService.startContainer(any(ModPack.class)))
                .thenThrow(new RuntimeException("Container vanished"));

        var response = post("/api/modpacks/" + result.upload().packId() + "/start", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void stopFailure_returns500() {
        var result = uploadAndDeploy();
        when(containerService.stopContainer(any(ModPack.class)))
                .thenThrow(new RuntimeException("Timeout"));

        var response = post("/api/modpacks/" + result.upload().packId() + "/stop", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void deleteFailure_returns500() {
        var upload = uploadForgePack();
        doThrow(new RuntimeException("Permission denied"))
                .when(containerService).deleteContainer(any(ModPack.class));

        var response = delete("/api/modpacks/delete?packId=" + upload.packId(), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
