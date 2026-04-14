package dk.mcmsm.dto.responses;

/**
 * Response payload for {@code GET /api/update/check}.
 *
 * @param currentVersion      version of the running application.
 * @param latestVersion       newest release tag on GitHub.
 * @param versionsBehind      total number of releases between current and latest (0 = up to date).
 * @param majorVersionsBehind number of major releases (X.0) between current and latest.
 * @param minorVersionsBehind number of minor/prerelease releases between current and latest.
 * @param patchVersionsBehind number of hotfix/patch releases (X.Y.Z) between current and latest.
 * @param updateAvailable     {@code true} when a newer release exists and the app runs from a JAR.
 * @param downloadUrl         browser URL for the latest release asset, or {@code null}.
 */
public record UpdateStatusResponse(
        String currentVersion,
        String latestVersion,
        int versionsBehind,
        int majorVersionsBehind,
        int minorVersionsBehind,
        int patchVersionsBehind,
        boolean updateAvailable,
        String downloadUrl
) {
}
