package dk.mcmsm.services.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Strategy for NeoForge mod loader detection, installation, and launch script generation.
 *
 * <p>NeoForge only exists for MC 1.20.1+, so it always uses the modern launch
 * pattern with libraries/ and unix_args.txt.</p>
 */
public final class NeoForgeLoaderStrategy implements LoaderStrategy {
    private static final Logger logger = LoggerFactory.getLogger(NeoForgeLoaderStrategy.class);

    private static final Pattern INSTALLER_PATTERN =
            Pattern.compile("neoforge-([\\d.]+)-installer\\.jar", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIX_ARGS_PATTERN =
            Pattern.compile("unix_args\\.txt");

    private static final String MAVEN_METADATA_URL =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final String MAVEN_BASE_URL =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

    @Override
    public LoaderType loaderType() {
        return LoaderType.NEOFORGE;
    }

    @Override
    public boolean detect(Path packDir, LoaderDetectionResult.Builder builder) {
        // Priority 1: Check for installer JAR
        var installerMatch = findInstallerJar(packDir);
        if (installerMatch.isPresent()) {
            var match = installerMatch.get();
            var mcVersion = neoforgeVersionToMcVersion(match.neoforgeVersion());
            builder.loaderType(LoaderType.NEOFORGE)
                    .installerJar(match.path())
                    .loaderVersion(match.neoforgeVersion())
                    .minecraftVersion(mcVersion);
            checkLibraries(packDir, builder);
            logger.info("Detected NeoForge installer: NeoForge={}, MC={}", match.neoforgeVersion(), mcVersion);
            return true;
        }

        // Priority 2: Check for libraries/net/neoforged/
        if (hasNeoForgeLibraries(packDir)) {
            builder.loaderType(LoaderType.NEOFORGE)
                    .hasLibraries(true);
            extractVersionFromLibraries(packDir, builder);
            logger.info("Detected installed NeoForge via libraries directory.");
            return true;
        }

        // Priority 3: Check .sh script content for neoforge references
        if (hasNeoForgeScriptReferences(packDir)) {
            builder.loaderType(LoaderType.NEOFORGE);
            logger.info("Detected NeoForge via script references.");
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
            var request = HttpRequest.newBuilder(URI.create(MAVEN_METADATA_URL))
                    .header("Accept", "application/xml")
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("NeoForge Maven metadata returned status {}.", response.statusCode());
                return Optional.empty();
            }

            return parseLatestNeoForgeVersion(response.body(), mcVersion);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to resolve NeoForge version for MC {}: {}", mcVersion, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<URI> installerDownloadUri(String mcVersion, String loaderVersion) {
        if (loaderVersion == null) {
            return Optional.empty();
        }
        var url = MAVEN_BASE_URL + loaderVersion + "/neoforge-" + loaderVersion + "-installer.jar";
        return Optional.of(URI.create(url));
    }

    @Override
    public String generateLaunchScript(Path packDir, LoaderDetectionResult detection) {
        var unixArgsPath = findUnixArgsFile(packDir);
        if (unixArgsPath.isEmpty()) {
            logger.warn("Could not find unix_args.txt for NeoForge in {}. Falling back to delegation.", packDir);
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

    /**
     * Converts a NeoForge version to its corresponding Minecraft version.
     * NeoForge version format: {@code major.minor.build} where MC = {@code 1.major.minor}.
     *
     * @param neoforgeVersion the NeoForge version (e.g., "21.1.211")
     * @return the Minecraft version (e.g., "1.21.1"), or null if parsing fails
     */
    static String neoforgeVersionToMcVersion(String neoforgeVersion) {
        if (neoforgeVersion == null) {
            return null;
        }
        var parts = neoforgeVersion.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            var major = Integer.parseInt(parts[0]);
            var minor = Integer.parseInt(parts[1]);
            if (minor == 0) {
                return "1." + major;
            }
            return "1." + major + "." + minor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converts a Minecraft version to the NeoForge version prefix for filtering Maven metadata.
     * MC {@code 1.21.1} → NeoForge prefix {@code 21.1.}.
     *
     * @param mcVersion the Minecraft version
     * @return the NeoForge version prefix, or null if parsing fails
     */
    private static String mcVersionToNeoForgePrefix(String mcVersion) {
        if (mcVersion == null) {
            return null;
        }
        var parts = mcVersion.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        // MC 1.X.Y → NeoForge X.Y.
        try {
            var mcMajor = parts.length >= 2 ? parts[1] : "0";
            var mcMinor = parts.length >= 3 ? parts[2] : "0";
            return mcMajor + "." + mcMinor + ".";
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<String> parseLatestNeoForgeVersion(String xmlBody, String mcVersion) {
        var prefix = mcVersionToNeoForgePrefix(mcVersion);
        if (prefix == null) {
            return Optional.empty();
        }

        try {
            var factory = DocumentBuilderFactory.newInstance();
            var docBuilder = factory.newDocumentBuilder();
            var doc = docBuilder.parse(new ByteArrayInputStream(xmlBody.getBytes(StandardCharsets.UTF_8)));
            var versionNodes = doc.getElementsByTagName("version");

            var matchingVersions = new ArrayList<String>();
            for (int i = 0; i < versionNodes.getLength(); i++) {
                var version = versionNodes.item(i).getTextContent().trim();
                if (version.startsWith(prefix)) {
                    matchingVersions.add(version);
                }
            }

            if (matchingVersions.isEmpty()) {
                logger.warn("No NeoForge versions found matching prefix '{}' for MC {}.", prefix, mcVersion);
                return Optional.empty();
            }

            // Sort by build number (last component) descending, pick highest
            matchingVersions.sort(Comparator.comparing(this::extractBuildNumber).reversed());
            return Optional.of(matchingVersions.getFirst());
        } catch (Exception e) {
            logger.warn("Failed to parse NeoForge Maven metadata: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private int extractBuildNumber(String version) {
        var parts = version.split("\\.");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private Optional<InstallerMatch> findInstallerJar(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        var matcher = INSTALLER_PATTERN.matcher(path.getFileName().toString());
                        if (matcher.matches()) {
                            return new InstallerMatch(path, matcher.group(1));
                        }
                        return null;
                    })
                    .filter(match -> match != null)
                    .findFirst();
        } catch (IOException e) {
            logger.warn("Failed to scan for NeoForge installer in {}: {}", packDir, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean hasNeoForgeLibraries(Path packDir) {
        return Files.isDirectory(packDir.resolve("libraries/net/neoforged"));
    }

    private void checkLibraries(Path packDir, LoaderDetectionResult.Builder builder) {
        if (Files.isDirectory(packDir.resolve("libraries"))) {
            builder.hasLibraries(true);
        }
    }

    private void extractVersionFromLibraries(Path packDir, LoaderDetectionResult.Builder builder) {
        var neoforgeLibDir = packDir.resolve("libraries/net/neoforged/neoforge");
        if (!Files.isDirectory(neoforgeLibDir)) {
            return;
        }

        try (Stream<Path> entries = Files.list(neoforgeLibDir)) {
            entries.filter(Files::isDirectory)
                    .map(dir -> dir.getFileName().toString())
                    .findFirst()
                    .ifPresent(version -> {
                        builder.loaderVersion(version);
                        var mcVersion = neoforgeVersionToMcVersion(version);
                        if (mcVersion != null) {
                            builder.minecraftVersion(mcVersion);
                        }
                    });
        } catch (IOException e) {
            logger.warn("Failed to extract version from NeoForge libraries: {}", e.getMessage());
        }
    }

    private boolean hasNeoForgeScriptReferences(Path packDir) {
        try (Stream<Path> entries = Files.list(packDir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".sh"))
                    .anyMatch(script -> {
                        try {
                            return Files.readString(script).toLowerCase().contains("neoforge");
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
            logger.warn("Failed to find unix_args.txt in NeoForge libraries: {}", e.getMessage());
            return Optional.empty();
        }
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
                echo "[McMSM] ERROR: Could not determine how to launch this NeoForge server."
                echo "[McMSM] Please select a different entry point in the metadata settings."
                exit 1
                """;
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

    private record InstallerMatch(Path path, String neoforgeVersion) {
    }
}
