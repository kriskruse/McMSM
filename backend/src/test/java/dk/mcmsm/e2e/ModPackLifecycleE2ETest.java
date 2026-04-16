package dk.mcmsm.e2e;

import dk.mcmsm.controller.GlobalExceptionHandler.ErrorResponse;
import dk.mcmsm.dto.responses.ModPackDeployResponseDto;
import dk.mcmsm.entities.ModPack;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModPackLifecycleE2ETest extends BaseE2ETest {

    @Test
    void deploy_returnsContainerInfo() {
        var upload = uploadForgePack();
        stubDeploy();

        var response = post("/api/modpacks/" + upload.packId() + "/deploy", ModPackDeployResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.containerId()).isEqualTo(FAKE_CONTAINER_ID);
        assertThat(body.image()).isEqualTo(FAKE_IMAGE);
        assertThat(body.status().getValue()).isEqualTo("running");
        assertThat(body.memoryLimitMiB()).isEqualTo(FAKE_MEMORY_LIMIT);
    }

    @Test
    void deploy_setsPackAsDeployed() {
        var upload = uploadForgePack();
        stubDeploy();
        post("/api/modpacks/" + upload.packId() + "/deploy", ModPackDeployResponseDto.class);

        var deployed = get("/api/modpacks/deployed", new ParameterizedTypeReference<List<ModPack>>() {});

        assertThat(deployed.getBody()).hasSize(1);
        assertThat(deployed.getBody().getFirst().getPackId()).isEqualTo(upload.packId());
    }

    @Test
    void start_returnsRunning() {
        var result = uploadAndDeploy();
        stubStart();

        var response = post("/api/modpacks/" + result.upload().packId() + "/start", ModPackDeployResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status().getValue()).isEqualTo("running");
    }

    @Test
    void stop_returnsStopped() {
        var result = uploadAndDeploy();
        stubStop();

        var response = post("/api/modpacks/" + result.upload().packId() + "/stop", ModPackDeployResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status().getValue()).isEqualTo("stopped");
    }

    @Test
    void archive_undeploysPack() {
        var result = uploadAndDeploy();
        stubDelete();

        var response = post("/api/modpacks/" + result.upload().packId() + "/archive", ModPackDeployResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status().getValue()).isEqualTo("not_deployed");
    }

    @Test
    void archive_packMovesToSaved() {
        var result = uploadAndDeploy();
        stubDelete();
        post("/api/modpacks/" + result.upload().packId() + "/archive", ModPackDeployResponseDto.class);

        var saved = get("/api/modpacks/saved", new ParameterizedTypeReference<List<ModPack>>() {});
        var deployed = get("/api/modpacks/deployed", new ParameterizedTypeReference<List<ModPack>>() {});

        assertThat(saved.getBody()).hasSize(1);
        assertThat(deployed.getBody()).isEmpty();
    }

    @Test
    void delete_returnsOk() {
        var upload = uploadForgePack();
        stubDelete();

        var response = delete("/api/modpacks/delete?packId=" + upload.packId(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("deleted");
    }

    @Test
    void delete_removesFromRepository() {
        var upload = uploadForgePack();
        stubDelete();
        delete("/api/modpacks/delete?packId=" + upload.packId(), String.class);

        var allPacks = get("/api/modpacks/", new ParameterizedTypeReference<List<ModPack>>() {});

        assertThat(allPacks.getBody()).isEmpty();
    }

    @Test
    void delete_removesDirectory() {
        var upload = uploadForgePack();
        var packPath = Path.of(upload.path());
        assertThat(Files.isDirectory(packPath)).isTrue();

        stubDelete();
        delete("/api/modpacks/delete?packId=" + upload.packId(), String.class);

        assertThat(Files.exists(packPath)).isFalse();
    }

    @Test
    void deploy_nonExistentPack_returns404() {
        var response = post("/api/modpacks/99999/deploy", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("99999");
    }
}
