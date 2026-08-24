package dk.mcmsm.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerRunAsResolverTest {

    @Test
    void usesConfiguredValueWhenPresent() {
        var resolver = new ContainerRunAsResolver("1000:1000", Path.of("/nonexistent/status"));
        assertEquals(Optional.of("1000:1000"), resolver.resolve());
    }

    @Test
    void acceptsSingleUidConfiguration() {
        var resolver = new ContainerRunAsResolver("1000", Path.of("/nonexistent/status"));
        assertEquals(Optional.of("1000"), resolver.resolve());
    }

    @Test
    void rejectsMalformedConfiguration() {
        var resolver = new ContainerRunAsResolver("root:root", Path.of("/nonexistent/status"));
        assertTrue(resolver.resolve().isEmpty());
    }

    @Test
    void parsesRealUidAndGidFromProcStatus(@TempDir Path tempDir) throws IOException {
        var procStatus = tempDir.resolve("status");
        Files.writeString(procStatus, """
                Name:   java
                Uid:    1001    1001    1001    1001
                Gid:    1002    1002    1002    1002
                """);

        var resolver = new ContainerRunAsResolver("", procStatus);

        assertEquals(Optional.of("1001:1002"), resolver.resolve());
    }

    @Test
    void returnsEmptyWhenProcStatusMissing() {
        var resolver = new ContainerRunAsResolver("", Path.of("/nonexistent/status"));
        assertTrue(resolver.resolve().isEmpty());
    }

    @Test
    void returnsEmptyWhenProcStatusLacksIdLines(@TempDir Path tempDir) throws IOException {
        var procStatus = tempDir.resolve("status");
        Files.writeString(procStatus, "Name:   java\nThreads: 42\n");

        var resolver = new ContainerRunAsResolver("", procStatus);

        assertTrue(resolver.resolve().isEmpty());
    }
}
