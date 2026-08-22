package com.university.lms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.dto.ApiErrorResponse;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ErrorCode;
import com.university.lms.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders authentication and authorization failures in the same {@link ApiErrorResponse} envelope
 * as every other error.
 *
 * <p><b>Why this class has to exist.</b> {@code GlobalExceptionHandler} already handles
 * {@code AuthenticationException} and {@code AccessDeniedException}, and it looks like that is
 * enough. It is not. {@code @RestControllerAdvice} only sees exceptions that escape a controller,
 * and the two most common security failures never reach one: a missing or invalid bearer token is
 * rejected by the security filter chain, well before the {@code DispatcherServlet} runs. Left
 * alone, those produce the servlet container's own error representation instead — a different
 * shape, on the exact responses a client is most likely to have to handle programmatically.
 *
 * <p>The advice methods stay: they cover {@code @PreAuthorize} denials raised inside the dispatch,
 * which <em>do</em> escape a controller. The two paths are complementary, not duplicated.
 *
 * <p>Neither response says why. "No token", "expired token", "wrong audience" and "bad signature"
 * are all {@code AUTHENTICATION_REQUIRED}, because a caller who is not authenticated has not
 * earned the right to probe the difference. The reason is logged against the same {@code traceId}
 * the client receives, so an operator can still tell them apart.
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorResponder.class);

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** No credentials, or credentials that did not validate. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        log.warn("Authentication failure on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        // RFC 6750 expects a challenge on a 401. Deliberately bare: Spring's default fills in an
        // error_description naming the precise decode failure, which hands an unauthenticated
        // caller a free oracle for probing tokens.
        response.setHeader("WWW-Authenticate", "Bearer");
        write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                CommonErrorCode.AUTHENTICATION_REQUIRED,
                "Authentication is required to access this resource");
    }

    /** Valid credentials that do not carry sufficient authority. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        log.warn("Access denied on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                CommonErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action");
    }

    private void write(
            HttpServletRequest request, HttpServletResponse response, HttpStatus status, ErrorCode code, String message)
            throws IOException {

        if (response.isCommitted()) {
            return;
        }
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(), code.code(), message, request.getRequestURI(), CorrelationIdFilter.current());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
