package dk.mcmsm.e2e;

import dk.mcmsm.controller.GlobalExceptionHandler.ErrorResponse;
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
    void update_nonExistentPack_returns404() {
        var response = postMultipart("/api/modpacks/99999/update", FORGE_PACK, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
