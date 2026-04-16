package dk.mcmsm.services;

import dk.mcmsm.entities.ModPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Calculates Docker container memory limits and reservations based on a modpack's
 * JVM configuration (Xmx, MaxDirectMemorySize parsed from user_jvm_args.txt).
 */
@Service
public class MemoryCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryCalculationService.class);

    /** Default heap size in MiB when Xmx cannot be parsed. */
    private static final int DEFAULT_XMX_MIB = 8192;

    /** Fixed overhead for JVM internals (metaspace, thread stacks, code cache). */
    private static final int JVM_OVERHEAD_MIB = 512;

    /** Default MaxDirectMemorySize when the JVM args file is missing or has no explicit value. */
    private static final int DEFAULT_DIRECT_MEMORY_MIB = 2048;

    /** Default Xmx value when not specified. */
    public static final String DEFAULT_JAVA_XMX = "5G";

    private static final Pattern XMX_PATTERN = Pattern.compile("(?i)^(?:-Xmx)?(\\d+)([mMgG])$");
    private static final Pattern DIRECT_MEMORY_PATTERN = Pattern.compile("(?i)^-XX:MaxDirectMemorySize=(\\d+)([mMgG])$");

    /**
     * Calculates the Docker container memory hard limit from the modpack's JVM Xmx setting
     * and MaxDirectMemorySize parsed from user_jvm_args.txt.
     *
     * @param modPack the modpack to resolve memory for
     * @return memory limit in MiB (Xmx + MaxDirectMemorySize + JVM overhead)
     */
    public int resolveContainerMemoryLimitMiB(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        var xmxMiB = parseXmxMiB(Objects.requireNonNullElse(modPack.getJavaXmx(), DEFAULT_JAVA_XMX)).orElse(DEFAULT_XMX_MIB);
        var directMemoryMiB = resolveDirectMemoryMiB(modPack);
        var limitMiB = xmxMiB + directMemoryMiB + JVM_OVERHEAD_MIB;
        logger.debug("Resolved memory limit for packId={}: xmxMiB={}, directMemoryMiB={}, jvmOverheadMiB={}, limitMiB={}",
                modPack.getPackId(), xmxMiB, directMemoryMiB, JVM_OVERHEAD_MIB, limitMiB);
        return limitMiB;
    }

    /**
     * Calculates the Docker memory reservation (soft limit) from the modpack's JVM Xmx setting.
     * The reservation represents the typical working set without burst off-heap allocations,
     * allowing Docker to reclaim idle memory under host pressure while still permitting
     * bursts up to the hard limit.
     *
     * @param modPack the modpack to resolve memory for
     * @return memory reservation in MiB (Xmx + JVM overhead, excluding MaxDirectMemorySize)
     */
    public int resolveContainerMemoryReservationMiB(ModPack modPack) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        var xmxMiB = parseXmxMiB(Objects.requireNonNullElse(modPack.getJavaXmx(), DEFAULT_JAVA_XMX)).orElse(DEFAULT_XMX_MIB);
        var reservationMiB = xmxMiB + JVM_OVERHEAD_MIB;
        logger.debug("Resolved memory reservation for packId={}: xmxMiB={}, jvmOverheadMiB={}, reservationMiB={}",
                modPack.getPackId(), xmxMiB, JVM_OVERHEAD_MIB, reservationMiB);
        return reservationMiB;
    }

    /**
     * Parses an Xmx token (e.g. "5G", "4096M", "-Xmx8G") into MiB.
     *
     * @param xmxToken the raw Xmx string
     * @return the value in MiB, or empty if the token is unparseable
     */
    OptionalInt parseXmxMiB(String xmxToken) {
        var matcher = XMX_PATTERN.matcher(Objects.requireNonNullElse(xmxToken, "").trim());
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }

        var value = Integer.parseInt(matcher.group(1));
        var unit = matcher.group(2).toUpperCase();
        var valueMiB = "G".equals(unit) ? value * 1024 : value;
        return OptionalInt.of(valueMiB);
    }

    private int resolveDirectMemoryMiB(ModPack modPack) {
        if (modPack.getPath() == null || modPack.getPath().isBlank()) {
            return DEFAULT_DIRECT_MEMORY_MIB;
        }
        var jvmArgsPath = Path.of(modPack.getPath()).toAbsolutePath().normalize().resolve("user_jvm_args.txt");
        try {
            var lines = Files.readAllLines(jvmArgsPath);
            return parseDirectMemoryMiB(lines).orElse(DEFAULT_DIRECT_MEMORY_MIB);
        } catch (IOException e) {
            logger.warn("Could not read user_jvm_args.txt for packId={}, using default direct memory {}MiB.", modPack.getPackId(), DEFAULT_DIRECT_MEMORY_MIB);
            return DEFAULT_DIRECT_MEMORY_MIB;
        }
    }

    private OptionalInt parseDirectMemoryMiB(List<String> lines) {
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            var matcher = DIRECT_MEMORY_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                var value = Integer.parseInt(matcher.group(1));
                var unit = matcher.group(2).toUpperCase();
                return OptionalInt.of("G".equals(unit) ? value * 1024 : value);
            }
        }
        return OptionalInt.empty();
    }
}
