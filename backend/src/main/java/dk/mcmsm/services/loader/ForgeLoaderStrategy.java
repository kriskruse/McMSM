package dk.mcmsm.services.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Strategy for Minecraft Forge mod loader detection, installation, and launch script generation.
 *
 * <p>Handles both legacy Forge (MC &lt; 1.17, uses universal JAR) and modern Forge
 * (MC &gt;= 1.17, uses libraries/ directory with unix_args.txt).</p>
 */
public final class ForgeLoaderStrategy implements LoaderStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ForgeLoaderStrategy.class);

    private static final Pattern INSTALLER_PATTERN =
            Pattern.compile("forge-([\\d.]+)-([\\d.]+)-installer\\.jar", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIVERSAL_JAR_PATTERN =
            Pattern.compile("forge-([\\d.]+)-([\\d.]+)(?:-universal)?\\.jar", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIX_ARGS_PATTERN =
            Pattern.compile("unix_args\\.txt");

    private static final String PROMOTIONS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String MAVEN_BASE_URL =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/";

    /**
     * Minecraft versions below this threshold use legacy Forge (no libraries/ directory,
     * no @argfile support in Java 8).
     */
    private static final String MODERN_FORGE_THRESHOLD = "1.17";

    private final Gson gson = new Gson();

    @Override
    public LoaderType loaderType() {
        return LoaderType.FORGE;
    }

    @Override
    public boolean detect(Path packDir, LoaderDetectionResult.Builder builder) {
        // Priority 1: Check for installer JAR
        var installerMatch = findInstallerJar(packDir);
        if (installerMatch.isPresent()) {
            var match = installerMatch.get();
            builder.loaderType(LoaderType.FORGE)
                    .installerJar(match.path())
                    .minecraftVersion(match.mcVersion())
                    .loaderVersion(match.loaderVersion())
                    .isLegacyForge(isLegacyMinecraftVersion(match.mcVersion()));
            checkLibraries(packDir, builder);
            logger.info("Detected Forge installer: MC={}, Forge={}", match.mcVersion(), match.loaderVersion());
            return true;
        }

        // Priority 2: Check for libraries/net/minecraftforge/
        if (hasForgeLibraries(packDir)) {
            builder.loaderType(LoaderType.FORGE)
                    .hasLibraries(true);
            extractVersionFromLibraries(packDir, builder);
            logger.info("Detected installed Forge via libraries directory.");
            return true;
        }

        // Priority 3: Check for legacy universal JAR
        var universalMatch = findUniversalJar(packDir);
        if (universalMatch.isPresent()) {
            var match = universalMatch.get();
            builder.loaderType(LoaderType.FORGE)
                    .serverJar(match.path())
                    .minecraftVersion(match.mcVersion())
                    .loaderVersion(match.loaderVersion())
                    .isLegacyForge(true);
            logger.info("Detected legacy Forge universal JAR: MC={}, Forge={}", match.mcVersion(), match.loaderVersion());
            return true;
        }

        // Priority 4: Check .sh script content for forge references
        if (hasForgeScriptReferences(packDir)) {
            builder.loaderType(LoaderType.FORGE);
            logger.info("Detected Forge via script references.");
            return true;
        }

        return false;
    }

    @Override
    public List<String> installerArguments(Path installerJar, Path packDir) {
        return List.of("java", "-jar", installerJar.toAbsolutePath().toString(), "--installServer");
    }

    @Override
    public Optional<String> resolveLatestVersion(String mcVersion, HttpClient httpClient) {
        try {
            var request = HttpRequest.newBuilder(URI.create(PROMOTIONS_URL))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("Forge promotions API returned status {}.", response.statusCode());
                return Optional.empty();
            }

            var json = gson.fromJson(response.body(), JsonObject.class);
            var promos = json.getAsJsonObject("promos");
            if (promos == null) {
                return Optional.empty();
            }

            // Prefer recommended, fall back to latest
            var recommendedKey = mcVersion + "-recommended";
            var latestKey = mcVersion + "-latest";

            if (promos.has(recommendedKey)) {
                return Optional.of(promos.get(recommendedKey).getAsString());
            }
            if (promos.has(latestKey)) {
                return Optional.of(promos.get(latestKey).getAsString());
            }

            logger.warn("No Forge version found for MC {} in promotions.", mcVersion);
            return Optional.empty();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to resolve Forge version for MC {}: {}", mcVersion, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<URI> installerDownloadUri(String mcVersion, String loaderVersion) {
        if (mcVersion == null || loaderVersion == null) {
            return Optional.empty();
        }
        var versionKey = mcVersion + "-" + loaderVersion;
        var url = MAVEN_BASE_URL + versionKey + "/forge-" + versionKey + "-installer.jar";
        return Optional.of(URI.create(url));
    }

    @Override
    public String generateLaunchScript(Path packDir, LoaderDetectionResult detection) {
        if (detection.isLegacyForge()) {
            return generateLegacyScript(packDir, detection);
        }
        return generateModernScript(packDir, detection);
    }

    private String generateModernScript(Path packDir, LoaderDetectionResult detection) {
        var unixArgsPath = findUnixArgsFile(packDir);
        if (unixArgsPath.isEmpty()) {
            logger.warn("Could not find unix_args.txt for modern Forge in {}. Falling back to delegation.", packDir);
            return generateFallbackScript(packDir);
        }

        var relativePath = packDir.relativize(unixArgsPath.get()).toString().replace('\\', '/');
        return """
                #!/usr/bin/env bash
                set -eu
                cd "$(dirname "$0")"
                exec java @user_jvm_args.txt @%s nogui "$@"
                """.formatted(relativePath);
    }

    private String generateLegacyScript(Path packDir, LoaderDetectionResult detection) {
        var serverJarName = resolveServerJarName(packDir, detection);
        // Legacy Forge uses Java 8 which does not support @argfile syntax.
        // Extract JVM args from our user_jvm_args.txt and pass them directly.
        return """
                #!/usr/bin/env bash
                set -eu
                cd "$(dirname "$0")"
                JVM_ARGS=$(grep -v '^\\s*#' user_jvm_args.txt | grep -v '^\\s*$' | tr '\\n' ' ')
                exec java $JVM_ARGS -jar %s nogui "$@"
                """.formatted(serverJarName);
    }

    private String generateFallbackScript(Path packDir) {
        var firstSh = findFirstShScript(packDir);
        if (firstSh != null) {
            return """
                    #!/usr/bin/env bash
                    set -eu
                    cd "$(dirname "$0")"
                    exec bash ./%s "$@"
                    """.formatted(firstSh);
        }
        return """
                #!/usr/bin/env bash
                set -eu
                cd "$(dirname "$0")"
                echo "[McMSM] ERROR: Could not determine how to launch this Forge server."
                echo "[McMSM] Please select a different entry point in the metadata settings."
                exit 1
                """;
    }

    private Optional<JarMatch> findInstallerJar(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        var matcher = INSTALLER_PATTERN.matcher(path.getFileName().toString());
                        if (matcher.matches()) {
                            return new JarMatch(path, matcher.group(1), matcher.group(2));
                        }
                        return null;
                    })
                    .filter(match -> match != null)
                    .findFirst();
        } catch (IOException e) {
            logger.warn("Failed to scan for Forge installer in {}: {}", packDir, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<JarMatch> findUniversalJar(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        var name = path.getFileName().toString();
                        // Exclude installer JARs
                        if (name.toLowerCase().contains("installer")) {
                            return null;
                        }
                        var matcher = UNIVERSAL_JAR_PATTERN.matcher(name);
                        if (matcher.matches()) {
                            return new JarMatch(path, matcher.group(1), matcher.group(2));
                        }
                        return null;
                    })
                    .filter(match -> match != null)
                    .findFirst();
        } catch (IOException e) {
            logger.warn("Failed to scan for Forge universal JAR in {}: {}", packDir, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean hasForgeLibraries(Path packDir) {
        return Files.isDirectory(packDir.resolve("libraries/net/minecraftforge"));
    }

    private void checkLibraries(Path packDir, LoaderDetectionResult.Builder builder) {
        if (Files.isDirectory(packDir.resolve("libraries"))) {
            builder.hasLibraries(true);
        }
    }

    private void extractVersionFromLibraries(Path packDir, LoaderDetectionResult.Builder builder) {
        var forgeLibDir = packDir.resolve("libraries/net/minecraftforge/forge");
        if (!Files.isDirectory(forgeLibDir)) {
            return;
        }

        try (Stream<Path> entries = Files.list(forgeLibDir)) {
            entries.filter(Files::isDirectory)
                    .map(dir -> dir.getFileName().toString())
                    .filter(name -> name.contains("-"))
                    .findFirst()
                    .ifPresent(versionDir -> {
                        var parts = versionDir.split("-", 2);
                        if (parts.length == 2) {
                            builder.minecraftVersion(parts[0])
                                    .loaderVersion(parts[1])
                                    .isLegacyForge(isLegacyMinecraftVersion(parts[0]));
                        }
                    });
        } catch (IOException e) {
            logger.warn("Failed to extract version from Forge libraries: {}", e.getMessage());
        }
    }

    private boolean hasForgeScriptReferences(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".sh"))
                    .anyMatch(script -> {
                        try {
                            var content = Files.readString(script).toLowerCase();
                            return content.contains("forge") && !content.contains("neoforge");
                        } catch (IOException e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
    }

    private Optional<Path> findUnixArgsFile(Path packDir) {
        var librariesDir = packDir.resolve("libraries");
        if (!Files.isDirectory(librariesDir)) {
            return Optional.empty();
        }

        try (Stream<Path> tree = Files.walk(librariesDir)) {
            return tree
                    .filter(Files::isRegularFile)
                    .filter(p -> UNIX_ARGS_PATTERN.matcher(p.getFileName().toString()).matches())
                    .findFirst();
        } catch (IOException e) {
            logger.warn("Failed to find unix_args.txt in libraries: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String resolveServerJarName(Path packDir, LoaderDetectionResult detection) {
        if (detection.serverJar() != null) {
            return detection.serverJar().getFileName().toString();
        }

        // Try to find a forge JAR in the pack directory
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.toLowerCase().startsWith("forge-") && name.endsWith(".jar"))
                    .filter(name -> !name.toLowerCase().contains("installer"))
                    .findFirst()
                    .orElse("forge-server.jar");
        } catch (IOException e) {
            return "forge-server.jar";
        }
    }

    private String findFirstShScript(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".sh") && !name.equals("mcmsm-launch.sh"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isLegacyMinecraftVersion(String mcVersion) {
        if (mcVersion == null) {
            return false;
        }
        return compareVersions(mcVersion, MODERN_FORGE_THRESHOLD) < 0;
    }

    private int compareVersions(String left, String right) {
        var leftParts = left.split("\\.");
        var rightParts = right.split("\\.");
        var maxLen = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < maxLen; i++) {
            var l = i < leftParts.length ? parseIntSafe(leftParts[i]) : 0;
            var r = i < rightParts.length ? parseIntSafe(rightParts[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record JarMatch(Path path, String mcVersion, String loaderVersion) {
    }
}
