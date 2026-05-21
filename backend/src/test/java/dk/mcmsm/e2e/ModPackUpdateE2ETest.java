package dk.mcmsm.e2e;

import dk.mcmsm.controller.GlobalExceptionHandler.ErrorResponse;
import dk.mcmsm.dto.requests.ModPackMetadataRequestDto;
import dk.mcmsm.dto.responses.ModPackMetadataResponseDto;
import dk.mcmsm.dto.responses.ModPackUploadResponseDto;
import dk.mcmsm.entities.ModPack;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModPackUpdateE2ETest extends BaseE2ETest {

    @Test
    void update_renamesOldPack() {
        var result = uploadAndDeploy();
        stubStop();
        stubDelete();
        stubDeploy();

        postMultipart(
                "/api/modpacks/" + result.upload().packId() + "/update",
                NEO_PACK,
                ModPackUploadResponseDto.class
        );

        var allPacks = get("/api/modpacks/", new ParameterizedTypeReference<List<ModPack>>() {});

        assertThat(allPacks.getBody()).isNotNull();
        var oldPack = allPacks.getBody().stream()
                .filter(p -> p.getPackId().equals(result.upload().packId()))
                .findFirst();
        assertThat(oldPack).isPresent();
        assertThat(oldPack.get().getName()).startsWith("(old) ");
    }

    @Test
    void update_preservesPersistentData() throws IOException {
        var result = uploadAndDeploy();
        var oldPackPath = Path.of(result.upload().path());
        Files.writeString(oldPackPath.resolve("whitelist.json"), "[\"player1\"]");
        stubStop();
        stubDelete();
        stubDeploy();

        postMultipart(
                "/api/modpacks/" + result.upload().packId() + "/update",
                NEO_PACK,
                ModPackUploadResponseDto.class
        );

        var allPacks = get("/api/modpacks/", new ParameterizedTypeReference<List<ModPack>>() {});
        var newPack = allPacks.getBody().stream()
                .filter(p -> !p.getPackId().equals(result.upload().packId()))
                .findFirst();

        assertThat(newPack).isPresent();
        var newWhitelist = Path.of(newPack.get().getPath()).resolve("whitelist.json");
        assertThat(Files.exists(newWhitelist)).isTrue();
        assertThat(Files.readString(newWhitelist)).contains("player1");
    }

    @Test
    void update_preservesUserCustomizedPortJavaAndXmx() throws IOException {
        var upload = uploadForgePack();

        var customMetadata = new ModPackMetadataRequestDto(
                "custom name",
                "v2",
                "1.20.1",
                17,
                "4G",
                "30000",
                upload.entryPoint()
        );
        var metadataResponse = post(
                "/api/modpacks/" + upload.packId() + "/metadata",
                customMetadata,
                ModPackMetadataResponseDto.class
        );
        assertThat(metadataResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        stubDeploy();
        post("/api/modpacks/" + upload.packId() + "/deploy", dk.mcmsm.dto.responses.ModPackDeployResponseDto.class);
        stubStop();
        stubDelete();

        var updateResponse = postMultipart(
                "/api/modpacks/" + upload.packId() + "/update",
                NEO_PACK,
                ModPackUploadResponseDto.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        var allPacks = get("/api/modpacks/", new ParameterizedTypeReference<List<ModPack>>() {});
        assertThat(allPacks.getBody()).isNotNull();

        var newPack = allPacks.getBody().stream()
                .filter(p -> !p.getPackId().equals(upload.packId()))
                .findFirst();
        assertThat(newPack).isPresent();
        assertThat(newPack.get().getPort()).isEqualTo("30000");
        assertThat(newPack.get().getJavaVersion()).isEqualTo(17);
        assertThat(newPack.get().getJavaXmx()).isEqualTo("4G");

        var serverProperties = Path.of(newPack.get().getPath()).resolve("server.properties");
        assertThat(Files.exists(serverProperties)).isTrue();
        assertThat(Files.readString(serverProperties)).contains("server-port=30000");

        var renamedOldPack = allPacks.getBody().stream()
                .filter(p -> p.getPackId().equals(upload.packId()))
                .findFirst();
        assertThat(renamedOldPack).isPresent();
        assertThat(renamedOldPack.get().getName()).startsWith("(old) ");
        assertThat(renamedOldPack.get().getPort()).isEqualTo("30000");
    }

    @Test
    void update_nonExistentPack_returns404() {
        var response = postMultipart("/api/modpacks/99999/update", FORGE_PACK, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
