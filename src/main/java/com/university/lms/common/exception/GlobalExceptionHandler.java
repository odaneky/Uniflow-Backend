package com.university.lms.common.exception;

import com.university.lms.common.dto.ApiErrorResponse;
import com.university.lms.common.dto.ApiFieldError;
import com.university.lms.common.util.SensitiveDataMasker;
import com.university.lms.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception that escapes a controller into the single {@link ApiErrorResponse}
 * contract.
 *
 * <p>Two rules govern everything here. First, the response body never carries internal detail —
 * no stack traces, SQL, constraint names, or class names reach the client; those go to the log,
 * keyed by the same {@code traceId} the client receives, so an operator can join the two. Second,
 * the {@code code} is stable and machine-readable, so clients never have to parse prose.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_MESSAGE =
            "An unexpected error occurred. Quote the traceId when contacting support.";

    // ---------------------------------------------------------------------
    // Deliberate application errors
    // ---------------------------------------------------------------------

    /**
     * Covers the whole {@link ApplicationException} hierarchy in one place — the exception itself
     * already knows its stable code and its HTTP status.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplication(ApplicationException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        if (status.is5xxServerError()) {
            log.error("Application error [{}] on {}", ex.getErrorCode().code(), request.getRequestURI(), ex);
        } else {
            log.warn(
                    "Application error [{}] on {}: {}",
                    ex.getErrorCode().code(),
                    request.getRequestURI(),
                    ex.getMessage());
        }
        return build(status, ex.getErrorCode(), ex.getMessage(), request, List.of());
    }

    // ---------------------------------------------------------------------
    // Request validation
    // ---------------------------------------------------------------------

    /** Bean validation failure on an {@code @Valid @RequestBody} argument. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiFieldError> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return new ApiFieldError(
                                fieldError.getField(),
                                fieldError.getDefaultMessage(),
                                SensitiveDataMasker.maskIfSensitive(
                                        fieldError.getField(), fieldError.getRejectedValue()));
                    }
                    return ApiFieldError.of(error.getObjectName(), error.getDefaultMessage());
                })
                .sorted(Comparator.comparing(ApiFieldError::field, Comparator.nullsLast(String::compareTo)))
                .toList();

        log.warn("Validation failed on {} ({} field error(s))", request.getRequestURI(), errors.size());
        return build(
                HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR, "Request validation failed", request, errors);
    }

    /** Bean validation failure on a method parameter, e.g. {@code @Validated} on a path variable. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String field = lastPathNode(violation);
                    return new ApiFieldError(
                            field,
                            violation.getMessage(),
                            SensitiveDataMasker.maskIfSensitive(field, violation.getInvalidValue()));
                })
                .sorted(Comparator.comparing(ApiFieldError::field, Comparator.nullsLast(String::compareTo)))
                .toList();

        log.warn("Constraint violation on {} ({} violation(s))", request.getRequestURI(), errors.size());
        return build(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.CONSTRAINT_VIOLATION,
                "Request validation failed",
                request,
                errors);
    }

    /** Body could not be parsed at all — malformed JSON, wrong scalar type, empty body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        // The parser message can quote the offending payload, so it is logged but never returned.
        log.warn("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.MALFORMED_REQUEST,
                "Request body is missing or malformed",
                request,
                List.of());
    }

    /** A path variable or query parameter could not be converted to its declared type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String field = ex.getName();
        ApiFieldError error = new ApiFieldError(
                field,
                "must be a valid " + simpleTypeName(ex.getRequiredType()),
                SensitiveDataMasker.maskIfSensitive(field, ex.getValue()));

        log.warn("Type mismatch for '{}' on {}", field, request.getRequestURI());
        return build(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                request,
                List.of(error));
    }

    /** A required query parameter was absent. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        ApiFieldError error = ApiFieldError.of(ex.getParameterName(), "is required");
        log.warn("Missing parameter '{}' on {}", ex.getParameterName(), request.getRequestURI());
        return build(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                request,
                List.of(error));
    }

    /**
     * Defensive: a programming error should not surface as an opaque 500 when the cause is an
     * illegal argument that the caller can act on.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(
                HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR, "Request is not valid", request, List.of());
    }

    // ---------------------------------------------------------------------
    // Security
    // ---------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        // Never echo the reason: distinguishing "no such user" from "wrong password" is an oracle.
        log.warn("Authentication failure on {}", request.getRequestURI());
        return build(
                HttpStatus.UNAUTHORIZED,
                CommonErrorCode.AUTHENTICATION_REQUIRED,
                "Authentication is required to access this resource",
                request,
                List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}", request.getRequestURI());
        return build(
                HttpStatus.FORBIDDEN,
                CommonErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action",
                request,
                List.of());
    }

    // ---------------------------------------------------------------------
    // Persistence / concurrency
    // ---------------------------------------------------------------------

    /**
     * A database constraint rejected the write. Modules translate the cases they expect into a
     * precise {@link ResourceAlreadyExistsException}; anything reaching here is unanticipated, so
     * the constraint name — which leaks schema detail — is logged rather than returned.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation on {}", request.getRequestURI(), ex);
        return build(
                HttpStatus.CONFLICT,
                CommonErrorCode.DATA_INTEGRITY_VIOLATION,
                "The request conflicts with the current state of the resource",
                request,
                List.of());
    }

    /**
     * Two transactions raced on the same row and this one lost. Surfaced as 409 so the client can
     * simply retry with fresh state, which is the correct remedy.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic locking failure on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(
                HttpStatus.CONFLICT,
                CommonErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified concurrently. Reload and retry.",
                request,
                List.of());
    }

    // ---------------------------------------------------------------------
    // Protocol
    // ---------------------------------------------------------------------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(
                HttpStatus.METHOD_NOT_ALLOWED,
                CommonErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method not supported for this resource",
                request,
                List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Content type not supported",
                request,
                List.of());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        ApiFieldError error = ApiFieldError.of(ex.getRequestPartName(), "is required");
        log.warn("Missing multipart part '{}' on {}", ex.getRequestPartName(), request.getRequestURI());
        return build(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                request,
                List.of(error));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload too large on {}", request.getRequestURI());
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                CommonErrorCode.VALIDATION_ERROR,
                "File must be at most 12 MB",
                request,
                List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                CommonErrorCode.RESOURCE_NOT_FOUND,
                "No handler found for this path",
                request,
                List.of());
    }

    // ---------------------------------------------------------------------
    // Catch-all
    // ---------------------------------------------------------------------

    /**
     * Last line of defence. The client gets an opaque message plus the trace id; everything needed
     * to diagnose the failure goes to the log against that same id.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR, GENERIC_MESSAGE, request, List.of());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            List<ApiFieldError> errors) {

        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(), code.code(), message, request.getRequestURI(), CorrelationIdFilter.current(), errors);
        return ResponseEntity.status(status).body(body);
    }

    private static String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    private static String simpleTypeName(Class<?> type) {
        return type == null ? "value" : type.getSimpleName();
    }
}
