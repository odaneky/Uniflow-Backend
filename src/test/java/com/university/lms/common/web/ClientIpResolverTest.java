package com.university.lms.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.common.ratelimit.RateLimitProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Which address a request is attributed to.
 *
 * <p>Every case here is a bypass if it goes the wrong way: attribute to a forged header and the
 * limits are decorative; attribute to the proxy and every user behind a load balancer shares one
 * allowance, which is an outage rather than a defence.
 */
class ClientIpResolverTest {

    private static ClientIpResolver resolver(String... trustedProxies) {
        return new ClientIpResolver(new RateLimitProperties(true, 1024, List.of(trustedProxies), List.of()));
    }

    private static MockHttpServletRequest from(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    @DisplayName("with no trusted proxies, X-Forwarded-For is ignored entirely")
    void ignoresForwardedForByDefault() {
        assertThat(resolver().resolve(from("198.51.100.7", "1.2.3.4")))
                .as("believing a client-supplied header would let anyone mint a fresh bucket per request")
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("a request straight from an untrusted peer is attributed to that peer")
    void usesPeerWhenUntrusted() {
        assertThat(resolver("10.0.0.0/8").resolve(from("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("behind a trusted proxy, the forwarded client is used")
    void honoursForwardedForFromATrustedProxy() {
        assertThat(resolver("127.0.0.1/32").resolve(from("127.0.0.1", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /**
     * The left of the header is the part a caller can write; the right is what each hop observed.
     * Taking the rightmost untrusted entry stops at the first address a trusted hop actually saw.
     */
    @Test
    @DisplayName("a forged left-hand entry does not win over the real client")
    void takesTheRightmostUntrustedHop() {
        assertThat(resolver("127.0.0.1/32", "10.0.0.0/8")
                        .resolve(from("127.0.0.1", "9.9.9.9, 203.0.113.9, 10.0.0.5")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("an empty or malformed header falls back to the peer rather than failing")
    void toleratesRubbish() {
        ClientIpResolver resolver = resolver("127.0.0.1/32");
        assertThat(resolver.resolve(from("127.0.0.1", "   "))).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(from("127.0.0.1", ",,,"))).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(from("127.0.0.1", null))).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("an unattributable request gets a single shared bucket, not an exemption")
    void unknownPeerIsNotExempt() {
        assertThat(resolver().resolve(from(null, null))).isEqualTo("unknown");
    }
}
