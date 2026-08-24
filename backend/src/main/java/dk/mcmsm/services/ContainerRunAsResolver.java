package dk.mcmsm.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves the {@code uid:gid} pair that Minecraft server containers should run as,
 * so that server-generated files on bind mounts are owned by the same user as this
 * application and remain deletable by it.
 *
 * <p>Resolution order:</p>
 * <ol>
 *     <li>The explicitly configured value ({@code CONTAINER_USER} env var, format
 *     {@code uid} or {@code uid:gid})</li>
 *     <li>Auto-detection from {@code /proc/self/status} (Linux only)</li>
 * </ol>
 */
@Component
public class ContainerRunAsResolver {
    private static final Logger logger = LoggerFactory.getLogger(ContainerRunAsResolver.class);
    private static final String PROC_SELF_STATUS = "/proc/self/status";
    private static final Pattern RUN_AS_PATTERN = Pattern.compile("^\\d+(:\\d+)?$");

    private final String configuredRunAs;
    private final Path procStatusPath;

    /**
     * Creates the resolver with the configured container user.
     *
     * @param configuredRunAs configured {@code uid:gid} value, blank for auto-detection
     */
    @Autowired
    public ContainerRunAsResolver(@Value("${app.container.run-as:}") String configuredRunAs) {
        this(configuredRunAs, Path.of(PROC_SELF_STATUS));
    }

    /**
     * Creates the resolver with an explicit configuration and proc status location.
     *
     * @param configuredRunAs configured {@code uid:gid} value, blank for auto-detection
     * @param procStatusPath  path of the proc status file used for auto-detection
     */
    ContainerRunAsResolver(String configuredRunAs, Path procStatusPath) {
        this.configuredRunAs = Objects.toString(configuredRunAs, "");
        this.procStatusPath = procStatusPath;
    }

    /**
     * Resolves the user that server containers should run as.
     *
     * @return a {@code uid:gid} string, or empty when neither configuration nor
     *         auto-detection could produce a valid result
     */
    public Optional<String> resolve() {
        if (!configuredRunAs.isBlank()) {
            if (RUN_AS_PATTERN.matcher(configuredRunAs).matches()) {
                return Optional.of(configuredRunAs);
            }
            logger.warn("Ignoring invalid configured container user '{}', expected format 'uid' or 'uid:gid'.", configuredRunAs);
            return Optional.empty();
        }
        return detectFromProcSelfStatus();
    }

    private Optional<String> detectFromProcSelfStatus() {
        try {
            var content = Files.readString(procStatusPath);
            return parseProcStatus(content);
        } catch (IOException e) {
            logger.debug("Cannot auto-detect container user from {}: {}", procStatusPath, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses the real UID and GID from a {@code /proc/self/status} style document.
     *
     * @param statusContent full contents of a proc status file
     * @return {@code "uid:gid"} when both lines are present, otherwise empty
     */
    static Optional<String> parseProcStatus(String statusContent) {
        var realUid = extractRealId(statusContent, "Uid:");
        var realGid = extractRealId(statusContent, "Gid:");
        if (realUid.isEmpty() || realGid.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(realUid.get() + ":" + realGid.get());
    }

    private static Optional<String> extractRealId(String statusContent, String linePrefix) {
        return statusContent.lines()
                .filter(line -> line.startsWith(linePrefix))
                .map(line -> line.substring(linePrefix.length()).trim().split("\\s+"))
                .filter(fields -> fields.length > 0 && !fields[0].isBlank())
                .map(fields -> fields[0])
                .findFirst();
    }
}
