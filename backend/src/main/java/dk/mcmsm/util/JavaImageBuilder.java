package dk.mcmsm.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Component
public class JavaImageBuilder {

    // Replace with your project's logger, e.g. SLF4J.
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(JavaImageBuilder.class);
    private final DockerClient dockerClient;

    public JavaImageBuilder(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public synchronized String ensureImage(Integer javaVersion) {
        int version = javaVersion == null ? 21 : javaVersion;

        String baseImage = "eclipse-temurin:" + version + "-jre";
        String toolsImage = "minecraft-java:" + version + "-jre-tools";

        // Reuse the image if it has already been built.
        try {
            dockerClient.inspectImageCmd(toolsImage).exec();
            return toolsImage;
        } catch (NotFoundException ignored) {
            // Image does not exist; build it below.
        }

        // Use your existing helper to pull the base image.
        pullImageIfNeeded(baseImage);

        Path buildDir = null;

        try {
            buildDir = Files.createTempDirectory("minecraft-image-build-");

            String dockerfile = """
                    FROM %s
                    
                    USER root
                    
                    RUN apt-get update \\
                        && apt-get install -y --no-install-recommends \\
                           curl \\
                           wget \\
                           bash \\
                           procps \\
                           iputils-ping \\
                           net-tools \\
                           unzip \\
                        && rm -rf /var/lib/apt/lists/*
                    """.formatted(baseImage);

            Files.writeString(buildDir.resolve("Dockerfile"), dockerfile);

            logger.info("Building Docker image '{}'.", toolsImage);

            String imageId = dockerClient
                    .buildImageCmd(buildDir.toFile())
                    .withTags(Set.of(toolsImage))
                    .exec(new BuildImageResultCallback())
                    .awaitImageId();

            if (imageId == null || imageId.isBlank()) {
                throw new IllegalStateException(
                        "Docker image build returned no image ID: " + toolsImage);
            }

            logger.info(
                    "Built Docker image '{}' with ID '{}'.",
                    toolsImage, imageId);

            return toolsImage;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to prepare Docker build context for " + toolsImage, e);

        } finally {
            if (buildDir != null) {
                try (var paths = Files.walk(buildDir)) {
                    paths.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    logger.warn(
                                            "Could not delete temporary build file: {}",
                                            path, e);
                                }
                            });
                } catch (IOException e) {
                    logger.warn(
                            "Could not clean up temporary build directory: {}",
                            buildDir, e);
                }
            }
        }
    }

    private void pullImageIfNeeded(String image) {
        // Replace this with your existing implementation if it already
        // checks local images and pulls missing images.
        try {
            dockerClient.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            try {
                dockerClient.pullImageCmd(image)
                        .start()
                        .awaitCompletion();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while pulling image " + image, interrupted);
            }
        }
    }
}
