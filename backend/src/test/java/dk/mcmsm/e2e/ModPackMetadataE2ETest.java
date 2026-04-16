package dk.mcmsm.e2e;

import dk.mcmsm.controller.GlobalExceptionHandler.ErrorResponse;
import dk.mcmsm.dto.responses.ModPackMetadataResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModPackMetadataE2ETest extends BaseE2ETest {

    @Test
    void updateMetadata_changesName() {
        var upload = uploadForgePack();

        var response = post(
                "/api/modpacks/" + upload.packId() + "/metadata",
                Map.of("name", "My Custom Server"),
                ModPackMetadataResponseDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("My Custom Server");
    }

    @Test
    void updateMetadata_changesPort() {
        var upload = uploadForgePack();

        var response = post(
                "/api/modpacks/" + upload.packId() + "/metadata",
                Map.of("port", "25566"),
                ModPackMetadataResponseDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().port()).isEqualTo("25566");
    }

    @Test
    void updateMetadata_changesJavaVersion() {
        var upload = uploadForgePack();

        var response = post(
                "/api/modpacks/" + upload.packId() + "/metadata",
                Map.of("javaVersion", 17),
                ModPackMetadataResponseDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().javaVersion()).isEqualTo(17);
    }

    @Test
    void updateMetadata_partialUpdate_preservesOtherFields() {
        var upload = uploadForgePack();
        var originalPort = upload.port();
        var originalJavaVersion = upload.javaVersion();

        var response = post(
                "/api/modpacks/" + upload.packId() + "/metadata",
                Map.of("name", "Renamed Only"),
                ModPackMetadataResponseDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Renamed Only");
        assertThat(response.getBody().port()).isEqualTo(originalPort);
        assertThat(response.getBody().javaVersion()).isEqualTo(originalJavaVersion);
    }

    @Test
    void updateMetadata_nonExistentPack_returns404() {
        var response = post(
                "/api/modpacks/99999/metadata",
                Map.of("name", "Ghost"),
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("99999");
    }
}
