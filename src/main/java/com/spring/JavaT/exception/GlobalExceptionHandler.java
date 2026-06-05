package com.spring.JavaT.exception;

import com.spring.JavaT.common.ApiError;
import com.spring.JavaT.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>Every exception that escapes a controller is caught here and converted into
 * a consistent {@link ApiResponse} so clients always receive the same envelope
 * regardless of what went wrong.
 *
 * <p>Handler priority (most specific → least specific):
 * <ol>
 *   <li>Bean Validation — {@link MethodArgumentNotValidException}, {@link ConstraintViolationException}</li>
 *   <li>Custom business exceptions — {@link ResourceNotFoundException}, {@link DuplicateResourceException},
 *       {@link UnauthorizedException}, {@link ForbiddenException}, {@link BusinessException}</li>
 *   <li>Spring Security exceptions — {@link AuthenticationException}, {@link AccessDeniedException}</li>
 *   <li>Database exceptions — {@link DataIntegrityViolationException}, {@link TransactionSystemException}</li>
 *   <li>Spring MVC exceptions — bad request body, wrong method, missing params, type mismatch, 404</li>
 *   <li>Catch-all — {@link Exception}</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================================================================
    // 1. Validation errors
    // =========================================================================

    /**
     * Handles {@code @Valid} / {@code @Validated} failures on request bodies and
     * {@code @ModelAttribute} objects.
     *
     * <p>Each violated constraint becomes one {@link ApiError} entry. Field errors
     * include the field name and rejected value; global (class-level) errors do not.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiError> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fe) {
                        return ApiError.ofField(
                                fe.getField(),
                                fe.getDefaultMessage(),
                                fe.getRejectedValue(),
                                fe.getCode()
                        );
                    }
                    // global (class-level) constraint
                    return toGlobalApiError(error);
                })
                .toList();

        log.warn("Validation failed for request [{}]: {} error(s)", request.getRequestURI(), errors.size());

        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
    }

    /**
     * Handles {@code @Validated} constraint violations on path variables,
     * query parameters, and method-level constraints.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ApiError> errors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    // Extract the leaf field name from the full property path
                    String path = cv.getPropertyPath().toString();
                    String field = path.contains(".")
                            ? path.substring(path.lastIndexOf('.') + 1)
                            : path;
                    return ApiError.ofField(field, cv.getMessage(), cv.getInvalidValue(), null);
                })
                .toList();

        log.warn("Constraint violation for request [{}]: {} error(s)", request.getRequestURI(), errors.size());

        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
    }

    // =========================================================================
    // 2. Custom business exceptions
    // =========================================================================

    /** 404 — resource does not exist. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(),
                List.of(ApiError.ofGlobal(ex.getMessage(), ex.getErrorCode())), request);
    }

    /** 409 — duplicate / uniqueness violation. */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {

        log.warn("Duplicate resource [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage(),
                List.of(ApiError.ofGlobal(ex.getMessage(), ex.getErrorCode())), request);
    }

    /** 401 — missing or invalid authentication. */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {

        log.warn("Unauthorized access [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(),
                List.of(ApiError.ofGlobal(ex.getMessage(), ex.getErrorCode())), request);
    }

    /** 403 — authenticated but not permitted. */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {

        log.warn("Forbidden access [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(),
                List.of(ApiError.ofGlobal(ex.getMessage(), ex.getErrorCode())), request);
    }

    /**
     * Fallback for any other {@link BusinessException} subclass not handled above.
     * Uses the status code embedded in the exception itself.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException ex, HttpServletRequest request) {

        log.warn("Business exception [{}] status={}: {}", request.getRequestURI(), ex.getStatus(), ex.getMessage());

        List<ApiError> errors = ex.getErrorCode() != null
                ? List.of(ApiError.ofGlobal(ex.getMessage(), ex.getErrorCode()))
                : List.of(ApiError.ofGlobal(ex.getMessage()));

        return buildError(ex.getStatus(), ex.getMessage(), errors, request);
    }

    // =========================================================================
    // 3. Spring Security exceptions
    // =========================================================================

    /** 401 — wrong email or password on login. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        log.warn("Bad credentials [{}]", request.getRequestURI());
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid email or password",
                List.of(ApiError.ofGlobal("Invalid email or password", "BAD_CREDENTIALS")), request);
    }

    /**
     * Handles Spring Security's {@link AuthenticationException} (e.g. bad credentials,
     * expired token) that escapes the security filter chain into a controller.
     *
     * <p>Note: most 401s from Spring Security are handled by {@code AuthenticationEntryPoint}
     * before reaching here. This is a safety net for cases where auth checks happen
     * inside service/controller code.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failure [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, "Authentication required",
                List.of(ApiError.ofGlobal("Authentication required", "UNAUTHORIZED")), request);
    }

    /**
     * Handles Spring Security's {@link AccessDeniedException} that escapes the
     * security filter chain.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, "Access denied",
                List.of(ApiError.ofGlobal("You do not have permission to perform this action", "FORBIDDEN")), request);
    }

    // =========================================================================
    // 4. Database exceptions
    // =========================================================================

    /**
     * Handles database-level unique constraint violations.
     *
     * <p>Triggered when a {@code UNIQUE} index is violated at the DB level — for example,
     * a signup attempt with an email that already exists in the {@code users} table.
     * This fires even when no explicit pre-check was done in service code.
     *
     * <p>The raw database message (which contains table/column names and SQL details)
     * is intentionally suppressed. We inspect the cause chain to extract a clean
     * field hint where possible, and fall back to a generic message otherwise.
     *
     * <p>Returns 409 Conflict.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        // Log the full technical detail server-side for debugging
        log.warn("Data integrity violation [{}]: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        String clientMessage = extractDuplicateFieldHint(ex);

        return buildError(HttpStatus.CONFLICT, clientMessage,
                List.of(ApiError.ofGlobal(clientMessage, "DATA_INTEGRITY_VIOLATION")), request);
    }

    /**
     * Handles {@link ConstraintViolationException} that is wrapped inside a
     * {@link TransactionSystemException} when Bean Validation fires at transaction
     * commit time (i.e. inside a {@code @Transactional} method rather than at the
     * controller boundary).
     *
     * <p>This is a common gotcha: {@code @Valid} on a controller catches violations
     * before the method runs, but JPA entity-level {@code @Column(nullable=false)} or
     * Hibernate Validator annotations on the entity itself are checked at flush/commit
     * time and arrive here instead.
     *
     * <p>Returns 400 Bad Request.
     */
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransactionSystemException(
            TransactionSystemException ex, HttpServletRequest request) {

        Throwable cause = ex.getRootCause();

        if (cause instanceof ConstraintViolationException cve) {
            // Delegate to the existing constraint violation handler
            return handleConstraintViolation(cve, request);
        }

        // Not a validation issue — treat as a generic server error
        log.error("Transaction system exception [{}]", request.getRequestURI(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                List.of(ApiError.ofGlobal("An unexpected error occurred. Please try again later.", "INTERNAL_ERROR")),
                request);
    }

    /**
     * Handles {@link JpaSystemException} wrapping a {@link ConstraintViolationException}
     * thrown by Hibernate outside of a transaction boundary.
     *
     * <p>Returns 400 Bad Request.
     */
    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleJpaSystemException(
            JpaSystemException ex, HttpServletRequest request) {

        Throwable cause = ex.getRootCause();

        if (cause instanceof ConstraintViolationException cve) {
            return handleConstraintViolation(cve, request);
        }

        log.error("JPA system exception [{}]", request.getRequestURI(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                List.of(ApiError.ofGlobal("An unexpected error occurred. Please try again later.", "INTERNAL_ERROR")),
                request);
    }

    // =========================================================================
    // 5. Spring MVC exceptions
    // =========================================================================

    /**
     * Handles malformed or unreadable JSON request bodies.
     * Returns 400 without leaking internal deserialization details.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Unreadable request body [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body",
                List.of(ApiError.ofGlobal("Request body is missing or contains invalid JSON", "INVALID_REQUEST_BODY")),
                request);
    }

    /**
     * Handles requests made with an HTTP method not supported by the endpoint
     * (e.g. POST to a GET-only endpoint).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String message = "HTTP method '%s' is not supported for this endpoint".formatted(ex.getMethod());
        log.warn("Method not supported [{}]: {}", request.getRequestURI(), message);
        return buildError(HttpStatus.METHOD_NOT_ALLOWED, message,
                List.of(ApiError.ofGlobal(message, "METHOD_NOT_ALLOWED")), request);
    }

    /**
     * Handles missing required query or form parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String message = "Required parameter '%s' is missing".formatted(ex.getParameterName());
        log.warn("Missing request parameter [{}]: {}", request.getRequestURI(), message);
        return buildError(HttpStatus.BAD_REQUEST, message,
                List.of(ApiError.ofField(ex.getParameterName(), message, null, "MISSING_PARAMETER")), request);
    }

    /**
     * Handles type mismatches in path variables or query parameters
     * (e.g. passing "abc" for a {@code Long} path variable).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String message  = "Parameter '%s' must be of type %s".formatted(ex.getName(), expected);
        log.warn("Type mismatch [{}]: {}", request.getRequestURI(), message);
        return buildError(HttpStatus.BAD_REQUEST, message,
                List.of(ApiError.ofField(ex.getName(), message, ex.getValue(), "TYPE_MISMATCH")), request);
    }

    /**
     * Handles requests to URLs that don't match any registered route (Spring 6+ static resource 404).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        String message = "No endpoint found for [%s] %s".formatted(ex.getHttpMethod(), request.getRequestURI());
        log.warn("No resource found: {}", message);
        return buildError(HttpStatus.NOT_FOUND, message,
                List.of(ApiError.ofGlobal(message, "ENDPOINT_NOT_FOUND")), request);
    }

    // =========================================================================
    // 6. Catch-all
    // =========================================================================

    /**
     * Safety net for any unhandled exception.
     *
     * <p>Returns 500 with a generic message. The real cause is logged server-side
     * but never exposed to the client to avoid leaking implementation details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for request [{}]", request.getRequestURI(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                List.of(ApiError.ofGlobal("An unexpected error occurred. Please try again later.", "INTERNAL_ERROR")),
                request);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ResponseEntity<ApiResponse<Void>> buildError(
            HttpStatus status, String message, List<ApiError> errors, HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(
                        ApiResponse.<Void>builder()
                                .success(false)
                                .message(message)
                                .errors(errors)
                                .path(request.getRequestURI())
                                .build()
                );
    }

    private ApiError toGlobalApiError(ObjectError error) {
        return ApiError.builder()
                .message(error.getDefaultMessage())
                .code(error.getCode())
                .build();
    }

    /**
     * Attempts to extract a human-readable field hint from a {@link DataIntegrityViolationException}.
     *
     * <p>PostgreSQL unique violation messages follow the pattern:
     * {@code ERROR: duplicate key value violates unique constraint "users_email_key"
     * Detail: Key (email)=(john@example.com) already exists.}
     *
     * <p>We look for the {@code Key (fieldName)=} pattern in the cause message and
     * build a clean message from it. If parsing fails we return a safe generic message.
     */
    private String extractDuplicateFieldHint(DataIntegrityViolationException ex) {
        String causeMessage = ex.getMostSpecificCause().getMessage();

        if (causeMessage != null) {
            // PostgreSQL: "Key (email)=(john@example.com) already exists."
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("Key \\((.+?)\\)=\\((.+?)\\) already exists")
                    .matcher(causeMessage);

            if (matcher.find()) {
                String field = matcher.group(1);   // e.g. "email"
                String value = matcher.group(2);   // e.g. "john@example.com"
                return "%s '%s' is already in use".formatted(
                        Character.toUpperCase(field.charAt(0)) + field.substring(1),
                        value
                );
            }

            // MySQL / MariaDB: "Duplicate entry 'john@example.com' for key 'users.email'"
            matcher = java.util.regex.Pattern
                    .compile("Duplicate entry '(.+?)' for key '(.+?)'")
                    .matcher(causeMessage);

            if (matcher.find()) {
                String value   = matcher.group(1);  // e.g. "john@example.com"
                String keyName = matcher.group(2);  // e.g. "users.email"
                // Extract just the column part after the dot if present
                String field = keyName.contains(".") ? keyName.substring(keyName.lastIndexOf('.') + 1) : keyName;
                return "%s '%s' is already in use".formatted(
                        Character.toUpperCase(field.charAt(0)) + field.substring(1),
                        value
                );
            }
        }

        // Safe fallback — no SQL details exposed
        return "A record with the provided details already exists";
    }
}
