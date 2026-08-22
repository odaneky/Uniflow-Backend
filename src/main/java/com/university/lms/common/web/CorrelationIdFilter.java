package com.university.lms.common.web;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes a correlation id for every request so that a single API call can be traced through
 * the logs — the thing you actually need when diagnosing a concurrency problem in production.
 *
 * <p>Honours an inbound {@code X-Correlation-Id} (so a client-generated id survives the hop) and
 * otherwise mints one. When Micrometer Tracing is active, {@code traceId} in MDC is the W3C trace
 * id and the inbound correlation value is stored as baggage; when it is not, the correlation id is
 * also the log {@code traceId}. The value is echoed on the response and is what surfaces as
 * {@code traceId} in an error body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "traceId";
    public static final String CORRELATION_MDC = "correlationId";

    /** Bounded so a hostile client cannot push arbitrary volume into every log line. */
    private static final int MAX_INBOUND_LENGTH = 64;

    private final ObjectProvider<Tracer> tracer;

    public CorrelationIdFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(CORRELATION_MDC, correlationId);

        Tracer tracing = tracer.getIfAvailable();
        BaggageInScope baggage = null;
        if (tracing != null) {
            baggage = tracing.createBaggageInScope("correlation.id", correlationId);
        } else {
            MDC.put(MDC_KEY, correlationId);
        }

        String echoed = correlationId;
        response.setHeader(HEADER, echoed);
        try {
            chain.doFilter(request, response);
        } finally {
            String traceId = MDC.get(MDC_KEY);
            if (StringUtils.hasText(traceId) && !response.isCommitted()) {
                response.setHeader(HEADER, traceId);
            }
            if (baggage != null) {
                baggage.close();
            }
            MDC.remove(CORRELATION_MDC);
            if (tracing == null) {
                MDC.remove(MDC_KEY);
            }
        }
    }

    private String resolve(String inbound) {
        if (!StringUtils.hasText(inbound)) {
            return UUID.randomUUID().toString();
        }
        String trimmed = inbound.trim();
        return trimmed.length() > MAX_INBOUND_LENGTH ? trimmed.substring(0, MAX_INBOUND_LENGTH) : trimmed;
    }

    /** Current request's trace or correlation id, or a placeholder when called outside a request. */
    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        String correlation = MDC.get(CORRELATION_MDC);
        return correlation != null ? correlation : "n/a";
    }
}
