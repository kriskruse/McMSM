package dk.mcmsm.e2e;

import dk.mcmsm.entities.ModPack;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModPackUploadE2ETest extends BaseE2ETest {

    @Test
    void upload_forge_returnsMetadata() {
        var dto = uploadTestPack(FORGE_PACK);

        assertThat(dto.packId()).isNotNull();
        assertThat(dto.name()).isNotBlank();
        assertThat(dto.path()).isNotBlank();
        assertThat(dto.entryPoint()).isNotBlank();
        assertThat(dto.javaVersion()).isNotNull();
        assertThat(dto.message()).contains("uploaded");
    }

    @Test
    void upload_neoForge_returnsMetadata() {
        var dto = uploadTestPack(NEO_PACK);

        assertThat(dto.packId()).isNotNull();
        assertThat(dto.name()).isNotBlank();
        assertThat(dto.path()).isNotBlank();
        assertThat(dto.entryPoint()).isNotBlank();
    }

    @Test
    void upload_createsDirectoryOnDisk() {
        var dto = uploadForgePack();

        assertThat(Files.isDirectory(Path.of(dto.path()))).isTrue();
    }

    @Test
    void upload_persistsToRepository() {
        var dto = uploadForgePack();

        var response = get("/api/modpacks/", new ParameterizedTypeReference<List<ModPack>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().getPackId()).isEqualTo(dto.packId());
    }

    @Test
    void getAllPacks_empty_returnsEmptyList() {
        var response = get("/api/modpacks/", new ParameterizedTypeReference<List<ModPack>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getSavedPacks_returnsOnlyNonDeployed() {
        uploadForgePack();

        var savedResponse = get("/api/modpacks/saved", new ParameterizedTypeReference<List<ModPack>>() {});
        var deployedResponse = get("/api/modpacks/deployed", new ParameterizedTypeReference<List<ModPack>>() {});

        assertThat(savedResponse.getBody()).hasSize(1);
        assertThat(deployedResponse.getBody()).isEmpty();
    }
}
