package dk.mcmsm.controller;

import dk.mcmsm.exception.ModPackFileException;
import dk.mcmsm.exception.ModPackNotFoundException;
import dk.mcmsm.exception.ModPackOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

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
     * Handles modpack file-system operation failures with a 500 response.
     *
     * @param ex the file exception
     * @return 500 response with error message
     */
    @ExceptionHandler(ModPackFileException.class)
    public ResponseEntity<ErrorResponse> handleFileError(ModPackFileException ex) {
        logger.error("Modpack file operation failed: {}", ex.getMessage(), ex);
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

    /**
     * Handles multipart upload errors (corrupt file, size exceeded) with a 400 response.
     *
     * @param ex the multipart exception
     * @return 400 response with error message
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartError(MultipartException ex) {
        logger.warn("Multipart error: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse("File upload failed: " + ex.getMessage()));
    }

    /**
     * Handles malformed or unreadable JSON request bodies with a 400 response.
     *
     * @param ex the message not readable exception
     * @return 400 response with error message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        logger.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse("Invalid request body."));
    }

    /**
     * Catch-all handler for unexpected exceptions with a 500 response.
     *
     * @param ex the unexpected exception
     * @return 500 response with generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(500).body(new ErrorResponse("An unexpected error occurred."));
    }
}
