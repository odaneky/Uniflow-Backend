package com.university.lms.common.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * HTTP-level rate limiting, applied before authentication.
 *
 * <p>This layer exists for callers who have not identified themselves — the admissions endpoints are
 * reachable without a token, so the only thing standing between them and an automated flood is this.
 * Per-user limits on authenticated actions are a separate concern and live in the services that own
 * them (see {@code CommsRateLimiter}); the two use different keys because they answer different
 * questions: "is this address abusing us" versus "is this person abusing us".
 *
 * @param maxTrackedClients hard ceiling on distinct keys held in memory. Without one, the limiter is
 *     itself a memory-exhaustion vector: an attacker rotating source addresses would grow the map
 *     until the process dies, which is a worse outcome than the flooding it was meant to stop.
 * @param trustedProxies CIDR ranges whose {@code X-Forwarded-For} may be believed. Empty by default,
 *     and that default matters — believing the header unconditionally lets any caller bypass every
 *     limit here by inventing a new address per request.
 * @param rules evaluated in order, first match wins, exactly like the authorization rules. A request
 *     matching no rule is not limited at this layer.
 */
@ConfigurationProperties("lms.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100000") int maxTrackedClients,
        @DefaultValue List<String> trustedProxies,
        @DefaultValue List<Rule> rules) {

    /**
     * @param method HTTP method, or {@code ANY}
     * @param path Ant-style pattern, e.g. {@code /api/v1/applications/**}
     * @param limit requests permitted per {@code window} per client
     */
    public record Rule(
            String name,
            @DefaultValue("ANY") String method,
            String path,
            int limit,
            @DefaultValue("1m") Duration window) {

        public Rule {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("every lms.rate-limit rule needs a name");
            }
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("rate-limit rule " + name + " needs a path");
            }
            if (limit <= 0) {
                // A limit of zero would block the endpoint outright, which is a routing decision
                // dressed up as a rate limit. Refuse it rather than silently deny all traffic.
                throw new IllegalArgumentException("rate-limit rule " + name + " needs a positive limit");
            }
        }

        boolean matchesMethod(String requestMethod) {
            return "ANY".equalsIgnoreCase(method) || method.equalsIgnoreCase(requestMethod);
        }
    }
}
