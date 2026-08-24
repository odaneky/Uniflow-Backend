package com.university.lms.common.web;

import com.university.lms.common.ratelimit.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Works out which address to hold responsible for a request.
 *
 * <p>{@code X-Forwarded-For} is client-supplied and trivially forged. Believing it unconditionally
 * would make every limit here decorative: an attacker sets a fresh address per request and each one
 * lands in its own bucket. So it is honoured only when the connection itself arrives from a
 * configured proxy — and with no proxies configured, which is the default, it is ignored entirely
 * and the socket address is used.
 *
 * <p>When the peer is trusted, the <em>rightmost untrusted</em> entry is taken rather than the
 * leftmost. The left of that header is the part an attacker can write; the right is what each hop
 * observed. Walking back from the end stops at the first address a trusted hop actually saw.
 */
/**
 * A shared bean rather than a rate-limit private detail: audit logging needs "who made this
 * request" for exactly the same reason rate limiting does, and the trust-boundary logic — honour
 * {@code X-Forwarded-For} only from a configured proxy, walk it from the right — must not be
 * reimplemented a second time with a chance to disagree with the first.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(RateLimitProperties properties) {
        this.trustedProxies = properties.trustedProxies() == null
                ? List.of()
                : properties.trustedProxies().stream()
                        .filter(cidr -> cidr != null && !cidr.isBlank())
                        .map(IpAddressMatcher::new)
                        .toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (peer == null) {
            // Nothing to attribute the request to. "unknown" is a single shared bucket, which is
            // deliberately strict: unattributable traffic should not get an unlimited allowance.
            return "unknown";
        }
        if (!isTrustedProxy(peer)) {
            return peer;
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !isTrustedProxy(hop)) {
                return hop;
            }
        }
        return peer;
    }

    private boolean isTrustedProxy(String address) {
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                // A malformed address cannot match a CIDR range; treat it as untrusted rather than
                // letting a bad header abort the request.
                return false;
            }
        }
        return false;
    }
}
