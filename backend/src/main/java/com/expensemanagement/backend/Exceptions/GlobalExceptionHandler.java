package com.expensemanagement.backend.Exceptions;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns domain exceptions into HTTP responses.
 *
 * <p>Without this, anything a service throws surfaces as a 500 with a stack trace, which makes
 * a wrong password look like a server bug.
 */
@RestControllerAdvice
@Slf4j
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

    /**
     * A parameter that could not be converted to its declared type - for example
     * {@code ?status=INVALID} against the {@code UserAccountState} enum, or
     * {@code /api/admin/users/abc} against a {@code Long} id.
     *
     * <p>Spring raises this before the controller is invoked, so without a handler it forwards
     * to {@code /error} and the client gets a different error shape from every other failure.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex)
    {
        String value = ex.getValue() == null ? "null" : ex.getValue().toString();
        String expected = ex.getRequiredType() == null
                ? "the expected type"
                : ex.getRequiredType().getSimpleName();

        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "invalid_parameter",
                        "Parameter '" + ex.getName() + "' has invalid value '" + value
                                + "'; expected " + expected));
    }

    /** A required query parameter was omitted, e.g. {@code /search} without {@code name}. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex)
    {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "missing_parameter",
                        "Required parameter '" + ex.getParameterName() + "' is missing"));
    }

    /**
     * An unknown field in {@code ?sort=...}, e.g. {@code ?sort=noSuchField}.
     *
     * <p>Spring Data resolves the sort property against the entity and throws this. Left
     * unhandled it surfaces as a 500, which is both wrong - the request is at fault, not the
     * server - and noisy: a client typo shows up in monitoring as a server error.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleInvalidSort(PropertyReferenceException ex)
    {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "invalid_sort",
                        "Unknown sort field '" + ex.getPropertyName() + "'"));
    }

    /**
     * Malformed values that reach the persistence layer - most often a {@code ?sort=} that
     * Spring Data accepts as a {@code Sort.Order} but JPA later rejects as not being a real
     * property reference.
     *
     * <p>Concretely this is how Swagger UI's {@code pageable} object used to arrive: a JSON
     * array literal, {@code ?sort=["userName,asc"]}, whose first comma-separated token parses
     * as the property name {@code ["userName}. It is a client mistake, so it must be a 400 -
     * left unhandled it was a 500.
     *
     * <p>Logged at WARN because the same exception would also be raised by a genuine misuse in
     * our own code, and that should be visible rather than silently reported as a bad request.
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiError> handleInvalidDataAccess(InvalidDataAccessApiUsageException ex)
    {
        log.warn("Rejecting request with a malformed query parameter", ex);

        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "invalid_query",
                        "A query parameter is malformed. For 'sort' use 'property,direction', "
                                + "e.g. sort=userName,asc - not a JSON array."));
    }
}
