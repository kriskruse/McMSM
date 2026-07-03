package dk.mcmsm.services;

import dk.mcmsm.dto.responses.ConfigFileDto;
import dk.mcmsm.entities.ModPack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModPackConfigServiceTest {

    private final ModPackConfigService service = new ModPackConfigService();

    private ModPack packAt(Path packDir) {
        var pack = new ModPack();
        pack.setPackId(42L);
        pack.setPath(packDir.toString());
        return pack;
    }

    @Test
    void listsOnlyJsonFilesRecursivelyWithRelativePaths(@TempDir Path packDir) throws IOException {
        var configDir = Files.createDirectories(packDir.resolve("config"));
        Files.writeString(configDir.resolve("root.json"), "{}");
        Files.writeString(configDir.resolve("notes.txt"), "ignore me");
        var jeiDir = Files.createDirectories(configDir.resolve("jei"));
        Files.writeString(jeiDir.resolve("jei.json"), "{}");

        List<ConfigFileDto> files = service.listConfigFiles(packAt(packDir));

        var relativePaths = files.stream().map(ConfigFileDto::relativePath).toList();
        assertEquals(List.of("jei/jei.json", "root.json"), relativePaths);
    }

    @Test
    void returnsEmptyListWhenNoConfigDirectory(@TempDir Path packDir) {
        assertTrue(service.listConfigFiles(packAt(packDir)).isEmpty());
    }

    @Test
    void readReturnsExactBytesIncludingComments(@TempDir Path packDir) throws IOException {
        var configDir = Files.createDirectories(packDir.resolve("config"));
        var content = "{\n  // a helpful comment\n  \"enabled\": true,\n}";
        Files.writeString(configDir.resolve("c.json"), content, StandardCharsets.UTF_8);

        assertEquals(content, service.readConfigFile(packAt(packDir), "c.json"));
    }

    @Test
    void writeRoundTripsExactBytes(@TempDir Path packDir) throws IOException {
        var configDir = Files.createDirectories(packDir.resolve("config"));
        Files.writeString(configDir.resolve("c.json"), "{}");
        var newContent = "{\n  // edited\n  \"value\": 5,\n}";

        service.writeConfigFile(packAt(packDir), "c.json", newContent);

        assertEquals(newContent, Files.readString(configDir.resolve("c.json"), StandardCharsets.UTF_8));
    }

    @Test
    void writeCreatesNoSiblingFiles(@TempDir Path packDir) throws IOException {
        var configDir = Files.createDirectories(packDir.resolve("config"));
        Files.writeString(configDir.resolve("c.json"), "{}");

        service.writeConfigFile(packAt(packDir), "c.json", "{\"x\":1}");

        try (var entries = Files.list(configDir)) {
            assertEquals(List.of("c.json"), entries.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void rejectsTraversalEscapeOnRead(@TempDir Path packDir) throws IOException {
        Files.createDirectories(packDir.resolve("config"));
        Files.writeString(packDir.resolve("secret.json"), "{}");

        assertThrows(IllegalArgumentException.class,
                () -> service.readConfigFile(packAt(packDir), "../secret.json"));
    }

    @Test
    void rejectsTraversalEscapeOnWrite(@TempDir Path packDir) throws IOException {
        Files.createDirectories(packDir.resolve("config"));

        assertThrows(IllegalArgumentException.class,
                () -> service.writeConfigFile(packAt(packDir), "../escaped.json", "{}"));
    }

    @Test
    void rejectsNonJsonSuffix(@TempDir Path packDir) throws IOException {
        Files.createDirectories(packDir.resolve("config"));

        assertThrows(IllegalArgumentException.class,
                () -> service.readConfigFile(packAt(packDir), "server.properties"));
    }

    @Test
    void rejectsBlankPath(@TempDir Path packDir) throws IOException {
        Files.createDirectories(packDir.resolve("config"));

        assertThrows(IllegalArgumentException.class,
                () -> service.readConfigFile(packAt(packDir), "  "));
    }
}
