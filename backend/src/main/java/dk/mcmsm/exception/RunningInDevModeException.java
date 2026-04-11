package dk.mcmsm.exception;

/**
 * Thrown when a method is called that is disabled in development mode. This is used to prevent certain operations (like updates) from being executed when the application is running in a non-production environment.
 */
public class RunningInDevModeException extends RuntimeException {


    public RunningInDevModeException(Class<?> clazz) {
        super("Running in dev mode - operation not allowed: " + clazz.getSimpleName());
    }

}
