package dk.mcmsm.e2e;

import dk.mcmsm.dto.responses.ModPackDeployResponseDto;
import dk.mcmsm.dto.responses.ModPackUploadResponseDto;
import dk.mcmsm.entities.ModPack;
import dk.mcmsm.services.ContainerLogService;
import dk.mcmsm.services.ContainerService;
import dk.mcmsm.services.ContainerService.DeploymentResult;
import dk.mcmsm.services.ContainerService.RuntimeState;
import dk.mcmsm.services.loader.LoaderDetectionResult;
import dk.mcmsm.services.loader.LoaderService;
import dk.mcmsm.services.loader.LoaderType;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Abstract base class for E2E tests.
 * Provides shared configuration, mock setup, cleanup, and HTTP helpers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class BaseE2ETest {

    static final String FORGE_PACK = "testModPacks/test_pack_forge_installer.zip";
    static final String NEO_PACK = "testModPacks/test_pack_neo_installer.zip";

    static final String FAKE_CONTAINER_ID = "fake-container-id-abc123";
    static final String FAKE_CONTAINER_NAME = "modpack-1-test-pack";
    static final String FAKE_IMAGE = "eclipse-temurin:21-jre";
    static final int FAKE_MEMORY_LIMIT = 7680;
    static final int FAKE_MEMORY_RESERVATION = 5632;

    /** Stable directory shared across all test classes so the cached Spring context stays valid. */
    private static final Path TEST_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "mcmsm-e2e-" + ProcessHandle.current().pid()
    );

    @MockitoBean
    ContainerService containerService;

    @MockitoBean
    ContainerLogService containerLogService;

    @MockitoBean
    LoaderService loaderService;

    @LocalServerPort
    int port;

    RestClient http;

    @DynamicPropertySource
    static void overrideStoragePaths(DynamicPropertyRegistry registry) {
        registry.add("app.storage.modpacks-root", () -> TEST_ROOT.resolve("modpacks").toString());
        registry.add("app.storage.metadata-root", () -> TEST_ROOT.resolve("data").toString());
        registry.add("app.storage.temp-root", () -> TEST_ROOT.resolve("temp").toString());
    }

    @BeforeEach
    void setUp() throws IOException {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        var dataDir = TEST_ROOT.resolve("data");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("modpacks.json"), "[]");
        Files.writeString(dataDir.resolve("users.json"), "[]");

        var modpacksDir = TEST_ROOT.resolve("modpacks");
        Files.createDirectories(modpacksDir);
        try (Stream<Path> entries = Files.list(modpacksDir)) {
            entries.forEach(path -> {
                try {
                    deleteRecursively(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        Mockito.reset(containerService, containerLogService, loaderService);
        stubLoaderService();
    }

    // ── HTTP helpers (non-throwing on 4xx/5xx) ──────────────────────────

    <T> ResponseEntity<T> get(String uri, Class<T> responseType) {
        return http.get()
                .uri(uri)
                .exchange((req, resp) -> toEntity(resp, responseType));
    }

    <T> ResponseEntity<T> get(String uri, ParameterizedTypeReference<T> typeRef) {
        return http.get()
                .uri(uri)
                .exchange((req, resp) -> toEntity(resp, typeRef));
    }

    <T> ResponseEntity<T> post(String uri, Object body, Class<T> responseType) {
        return http.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, resp) -> toEntity(resp, responseType));
    }

    <T> ResponseEntity<T> post(String uri, Class<T> responseType) {
        return http.post()
                .uri(uri)
                .exchange((req, resp) -> toEntity(resp, responseType));
    }

    <T> ResponseEntity<T> delete(String uri, Class<T> responseType) {
        return http.delete()
                .uri(uri)
                .exchange((req, resp) -> toEntity(resp, responseType));
    }

    <T> ResponseEntity<T> postMultipart(String uri, String classpathResource, Class<T> responseType) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource(classpathResource));

        return http.post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .exchange((req, resp) -> toEntity(resp, responseType));
    }

    // ── Upload helpers ──────────────────────────────────────────────────

    ModPackUploadResponseDto uploadTestPack(String classpathResource) {
        var response = postMultipart("/api/modpacks/upload", classpathResource, ModPackUploadResponseDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    ModPackUploadResponseDto uploadForgePack() {
        return uploadTestPack(FORGE_PACK);
    }

    // ── Mock helpers ────────────────────────────────────────────────────

    private void stubLoaderService() {
        when(loaderService.prepareModpack(any(Path.class)))
                .thenReturn(new LoaderDetectionResult(
                        LoaderType.FORGE,
                        "47.3.0",
                        "1.20.1",
                        null,
                        null,
                        true,
                        false,
                        List.of()
                ));
    }

    void stubDeploy() {
        when(containerService.deployServer(any(ModPack.class), anyInt(), anyInt()))
                .thenReturn(new DeploymentResult(
                        FAKE_CONTAINER_ID,
                        FAKE_CONTAINER_NAME,
                        FAKE_IMAGE,
                        FAKE_MEMORY_LIMIT,
                        FAKE_MEMORY_RESERVATION
                ));
    }

    void stubStart() {
        when(containerService.startContainer(any(ModPack.class)))
                .thenReturn(new RuntimeState(FAKE_CONTAINER_ID, FAKE_CONTAINER_NAME, true));
    }

    void stubStop() {
        when(containerService.stopContainer(any(ModPack.class)))
                .thenReturn(new RuntimeState(FAKE_CONTAINER_ID, FAKE_CONTAINER_NAME, false));
    }

    void stubDelete() {
        doNothing().when(containerService).deleteContainer(any(ModPack.class));
    }

    void stubLogs(String logContent) {
        when(containerLogService.readContainerLogs(any(ModPack.class), anyInt()))
                .thenReturn(logContent);
    }

    void stubCommand() {
        doNothing().when(containerLogService).executeCommand(any(ModPack.class), anyString());
    }

    UploadAndDeployResult uploadAndDeploy() {
        var upload = uploadForgePack();
        stubDeploy();
        var deployResponse = post("/api/modpacks/" + upload.packId() + "/deploy", ModPackDeployResponseDto.class);
        assertThat(deployResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new UploadAndDeployResult(upload, deployResponse.getBody());
    }

    record UploadAndDeployResult(ModPackUploadResponseDto upload, ModPackDeployResponseDto deploy) {}

    // ── Response conversion ─────────────────────────────────────────────

    private <T> ResponseEntity<T> toEntity(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse resp,
            Class<T> responseType
    ) throws IOException {
        var status = HttpStatusCode.valueOf(resp.getStatusCode().value());
        T body = resp.bodyTo(responseType);
        return ResponseEntity.status(status).headers(resp.getHeaders()).body(body);
    }

    private <T> ResponseEntity<T> toEntity(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse resp,
            ParameterizedTypeReference<T> typeRef
    ) throws IOException {
        var status = HttpStatusCode.valueOf(resp.getStatusCode().value());
        T body = resp.bodyTo(typeRef);
        return ResponseEntity.status(status).headers(resp.getHeaders()).body(body);
    }

    // ── Filesystem helpers ──────────────────────────────────────────────

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException e) { throw new UncheckedIOException(e); }
                    });
        }
    }
}
