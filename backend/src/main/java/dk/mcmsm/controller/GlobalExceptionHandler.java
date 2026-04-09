package dk.mcmsm.controller;

import dk.mcmsm.exception.ModPackNotFoundException;
import dk.mcmsm.exception.ModPackOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handling for all REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Error response body returned to the frontend.
     *
     * @param message human-readable error description
     */
    public record ErrorResponse(String message) {}

    /**
     * Handles modpack-not-found errors with a 404 response.
     *
     * @param ex the not-found exception
     * @return 404 response with error message
     */
    @ExceptionHandler(ModPackNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ModPackNotFoundException ex) {
        logger.warn("Modpack not found: packId={}", ex.getPackId());
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Handles modpack operation failures with a 500 response.
     *
     * @param ex the operation exception
     * @return 500 response with error message
     */
    @ExceptionHandler(ModPackOperationException.class)
    public ResponseEntity<ErrorResponse> handleOperationFailure(ModPackOperationException ex) {
        logger.error("Modpack operation failed: packId={}, operation='{}'", ex.getPackId(), ex.getOperation(), ex);
        return ResponseEntity.status(500).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Handles illegal argument errors with a 400 response.
     *
     * @param ex the illegal argument exception
     * @return 400 response with error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        logger.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Handles illegal state errors with a 500 response.
     *
     * @param ex the illegal state exception
     * @return 500 response with error message
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        logger.error("Illegal state: {}", ex.getMessage(), ex);
        return ResponseEntity.status(500).body(new ErrorResponse(ex.getMessage()));
    }
}
