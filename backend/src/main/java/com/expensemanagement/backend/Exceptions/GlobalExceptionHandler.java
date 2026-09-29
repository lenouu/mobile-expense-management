package com.expensemanagement.backend.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns domain exceptions into HTTP responses.
 *
 * <p>Without this, anything a service throws surfaces as a 500 with a stack trace, which makes
 * a wrong password look like a server bug.
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "not_found", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "conflict", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex)
    {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "invalid_credentials", ex.getMessage()));
    }

    @ExceptionHandler(OperationNotAllowedException.class)
    public ResponseEntity<ApiError> handleNotAllowed(OperationNotAllowedException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "operation_not_allowed", ex.getMessage()));
    }

    /**
     * Raised by {@code @PreAuthorize} when the caller lacks the required role.
     *
     * <p>Note the distinction from 401: 401 means "we do not know who you are", 403 means "we
     * know who you are and you are not allowed". The resource-server filter handles 401.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex)
    {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "forbidden", "Insufficient privileges"));
    }

    /** Raised when {@code @Valid} rejects a request body, e.g. a blank username. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex)
    {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "validation_failed", "Request validation failed", fieldErrors));
    }

    /**
     * Malformed JSON, or a body whose types do not match the DTO.
     *
     * <p>Without this the container forwards to {@code /error} and the client gets Spring's
     * generic error body instead of the same {@link ApiError} shape as every other failure.
     * The parser's own message is deliberately not echoed back - it leaks the DTO shape.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex)
    {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "malformed_request", "Request body is malformed or missing"));
    }

    /**
     * An unmapped URL - Spring MVC reports these as "no static resource", which is confusing
     * but arrives here as this exception.
     *
     * <p>Handled explicitly because otherwise the container forwards to {@code /error} and
     * Spring Boot's error body includes the full stack trace, complete with the security
     * filter chain. That is internal detail no client needs, and it is exactly the sort of
     * thing that ends up in a bug report screenshot.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "not_found", "No endpoint " + ex.getHttpMethod() + " " + ex.getResourcePath()));
    }
}
