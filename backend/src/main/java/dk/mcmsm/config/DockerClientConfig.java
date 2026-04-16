package dk.mcmsm.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Provides a singleton {@link DockerClient} bean for container operations.
 */
@Configuration
public class DockerClientConfig {

    /**
     * Creates and configures the Docker client with the host's default Docker daemon settings.
     *
     * @return a configured {@link DockerClient} instance
     */
    @Bean
    public DockerClient dockerClient() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(1))
                .responseTimeout(Duration.ofSeconds(5))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
