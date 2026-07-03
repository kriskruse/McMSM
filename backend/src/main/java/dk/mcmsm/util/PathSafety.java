package dk.mcmsm.util;

import java.nio.file.Path;

/**
 * Path-traversal safety helpers shared across services that resolve
 * user-supplied relative paths against a trusted root directory.
 */
public final class PathSafety {

    private PathSafety() {
    }

    /**
     * Verifies that a resolved path stays within its root directory.
     *
     * <p>Both arguments must already be normalized absolute paths. Used to defend
     * against path-traversal (e.g. {@code ../}) and absolute-path escapes when a
     * relative path originates from an untrusted source such as a request parameter.
     *
     * @param candidate the resolved, normalized path to validate.
     * @param root      the normalized root directory the candidate must stay within.
     * @param label     a short label for the candidate, used in the error message.
     * @throws IllegalArgumentException if the candidate escapes the root.
     */
    public static void ensureWithinRoot(Path candidate, Path root, String label) {
        if (candidate.startsWith(root)) {
            return;
        }

        throw new IllegalArgumentException("Resolved " + label + " path escaped its root: " + candidate);
    }
}
