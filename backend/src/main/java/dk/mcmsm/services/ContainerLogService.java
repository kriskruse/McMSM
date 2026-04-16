package dk.mcmsm.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import dk.mcmsm.entities.ModPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Handles Docker container log operations: reading logs, streaming live logs via SSE,
 * and executing console commands on running containers.
 */
@Service
public class ContainerLogService {

    private static final Logger logger = LoggerFactory.getLogger(ContainerLogService.class);

    private final DockerClient dockerClient;
    private final ContainerService containerService;

    public ContainerLogService(DockerClient dockerClient, ContainerService containerService) {
        this.dockerClient = dockerClient;
        this.containerService = containerService;
    }

    /**
     * Reads the tail of a container's log output.
     *
     * @param modPack   the target modpack
     * @param tailLines number of trailing lines to fetch
     * @return the log output as a string
     */
    public String readContainerLogs(ModPack modPack, int tailLines) {
        Objects.requireNonNull(modPack, "modPack must not be null");

        var containerId = containerService.resolveContainerId(modPack);
        if (containerId == null) {
            return "No container found for modpack " + modPack.getPackId() + ".";
        }

        var logsBuilder = new StringBuilder();
        try (var callback = new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame item) {
                if (item != null && item.getPayload() != null) {
                    logsBuilder.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                }
                super.onNext(item);
            }
        }) {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(Math.max(1, tailLines))
                    .exec(callback);
            callback.awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading logs for mod pack " + modPack.getPackId() + ".", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close log stream for mod pack " + modPack.getPackId() + ".", e);
        }

        return logsBuilder.toString().trim();
    }

    /**
     * Streams live container logs to an SSE emitter using docker-java's follow stream.
     * The returned callback must be closed when the SSE connection ends.
     *
     * @param modPack   the target modpack
     * @param emitter   the SSE emitter to send log lines to
     * @param tailLines initial number of historical lines to include
     * @return the closeable callback for cleanup on disconnect
     */
    public ResultCallback.Adapter<Frame> streamContainerLogs(ModPack modPack, SseEmitter emitter, int tailLines) {
        Objects.requireNonNull(modPack, "modPack must not be null");

        var containerId = containerService.resolveContainerId(modPack);
        if (containerId == null) {
            throw new IllegalStateException("No container found for modpack " + modPack.getPackId() + ".");
        }

        var callback = new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
                if (frame == null || frame.getPayload() == null) return;
                try {
                    var line = new String(frame.getPayload(), StandardCharsets.UTF_8);
                    emitter.send(SseEmitter.event().data(line).name("log"));
                } catch (IOException e) {
                    logger.debug("SSE send failed for packId={}, closing stream.", modPack.getPackId());
                    try { close(); } catch (IOException ignored) { /* already closing */ }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                logger.warn("Docker log stream error for packId={}: {}", modPack.getPackId(), throwable.getMessage());
                emitter.completeWithError(throwable);
            }

            @Override
            public void onComplete() {
                logger.debug("Docker log stream completed for packId={}", modPack.getPackId());
                emitter.complete();
            }
        };

        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(Math.max(1, tailLines))
                .withFollowStream(true)
                .exec(callback);

        return callback;
    }

    /**
     * Sends a console command to a running container by writing to PID 1's stdin.
     *
     * @param modPack the target modpack
     * @param command the console command to execute
     */
    public void executeCommand(ModPack modPack, String command) {
        Objects.requireNonNull(modPack, "modPack must not be null");
        Objects.requireNonNull(command, "command must not be null");

        var containerId = containerService.resolveRunningContainerId(modPack);
        if (containerId == null) {
            throw new IllegalStateException("No running container found for modpack " + modPack.getPackId() + ".");
        }

        var sanitized = sanitizeCommand(command);
        var execCreate = dockerClient.execCreateCmd(containerId)
                .withCmd("bash", "-c", "printf '%s\\n' " + sanitized + " > /proc/1/fd/0")
                .withAttachStdout(false)
                .withAttachStderr(false)
                .exec();

        dockerClient.execStartCmd(execCreate.getId())
                .withDetach(true)
                .exec(new ResultCallback.Adapter<>());

        logger.info("Sent command to packId={}: '{}'", modPack.getPackId(), command);
    }

    private String sanitizeCommand(String command) {
        var escaped = command
                .replace("\r", "")
                .replace("\n", "")
                .replace("'", "'\\''");
        return "'" + escaped + "'";
    }
}
