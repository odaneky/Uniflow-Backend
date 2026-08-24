package com.university.lms.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.web.ClientIpResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the rate-limit filter as infrastructure.
 *
 * <p>Explicit registration rather than component scanning, for a concrete reason: slice tests like
 * {@code @WebMvcTest} include every {@code Filter} bean they find but not arbitrary collaborators,
 * so a scanned filter with dependencies fails those contexts — and the failure surfaces as "cannot
 * create controller", pointing nowhere near the cause. A {@code @Configuration} is not part of a
 * web slice, so the filter simply does not exist there.
 *
 * <p>Ordered just after {@link com.university.lms.common.web.CorrelationIdFilter} and well ahead of
 * the security chain, so a rejection still carries a {@code traceId} but costs nothing else.
 */
@Configuration
class RateLimitConfiguration {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties,
            ClientIpResolver clientIpResolver,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {

        RateLimitFilter filter = new RateLimitFilter(properties, clientIpResolver, objectMapper, meterRegistry);

        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
