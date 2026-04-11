package dk.mcmsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the self-update feature.
 *
 * @param githubRepo      GitHub repository in "owner/repo" format.
 * @param checkIntervalMs Minimum interval between remote update checks, in milliseconds.
 */
@ConfigurationProperties(prefix = "app.update")
public record UpdateProperties(
        String githubRepo,
        long checkIntervalMs
) {
}
