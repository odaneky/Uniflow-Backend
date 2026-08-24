package com.university.lms.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.dto.ApiErrorResponse;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.web.ClientIpResolver;
import com.university.lms.common.web.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects abusive traffic before it costs anything.
 *
 * <p>Ordered ahead of the security filter chain deliberately. A flood aimed at a public endpoint
 * should be turned away before token parsing, database connections or transaction management are
 * involved — limiting after that work has already happened protects the database but not the
 * application in front of it.
 *
 * <p>Runs after {@link CorrelationIdFilter} so a rejection still carries a {@code traceId} and can
 * be tied to the request in the logs.
 *
 * <p>Registered by {@link RateLimitConfiguration} rather than annotated {@code @Component}. Slice
 * tests such as {@code @WebMvcTest} pull in every {@code Filter} bean they can see, but not the
 * collaborators this one needs — so component-scanning it broke every controller slice in the suite
 * with a context failure that pointed at the controller rather than at the filter.
 *
 * <p>The 429 body is written here rather than raised as an exception, for the same reason
 * {@code SecurityErrorResponder} exists: {@code @RestControllerAdvice} only sees exceptions that
 * escape a controller, and nothing here ever reaches one. Written by hand, it still matches the one
 * error envelope every other endpoint returns.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final BoundedFixedWindowRateLimiter limiter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(
            RateLimitProperties properties,
            ClientIpResolver clientIpResolver,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.limiter = new BoundedFixedWindowRateLimiter(properties.maxTrackedClients());
    }

    /**
     * Health and readiness are never limited.
     *
     * <p>Rate-limiting the liveness probe means a traffic spike causes the platform to conclude the
     * instance is dead and restart it — converting a load problem into an outage, at the worst
     * moment. Metrics are excluded for the same reason: observability must survive the incident it
     * is there to explain.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !properties.enabled() || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        RateLimitProperties.Rule rule = firstMatchingRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String client = clientIpResolver.resolve(request);
        BoundedFixedWindowRateLimiter.Decision decision =
                limiter.tryAcquire(rule.name() + ":" + client, rule.limit(), rule.window());

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        meterRegistry.counter("lms.rate_limit.rejected", "rule", rule.name()).increment();
        // The address is logged because that is the point of the record; never the token or body.
        log.warn(
                "Rate limit '{}' exceeded by {} on {} {}",
                rule.name(),
                client,
                request.getMethod(),
                request.getRequestURI());
        reject(request, response, decision.retryAfterSeconds());
    }

    private RateLimitProperties.Rule firstMatchingRule(HttpServletRequest request) {
        if (properties.rules() == null) {
            return null;
        }
        String path = request.getRequestURI();
        for (RateLimitProperties.Rule rule : properties.rules()) {
            if (rule.matchesMethod(request.getMethod()) && pathMatcher.match(rule.path(), path)) {
                return rule;
            }
        }
        return null;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                CommonErrorCode.RATE_LIMIT_EXCEEDED.code(),
                // Deliberately says nothing about which limit was hit or how much remains: that
                // would let a caller map the thresholds and pace themselves just underneath.
                "Too many requests. Try again later.",
                request.getRequestURI(),
                CorrelationIdFilter.current());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
