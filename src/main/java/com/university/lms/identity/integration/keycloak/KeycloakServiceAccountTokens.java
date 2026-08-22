package com.university.lms.identity.integration.keycloak;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.identity.domain.IdentityErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Supplies and caches the backend's own service-account token.
 *
 * <p>Cached until shortly before expiry. Without caching, every administrative call would perform a
 * client-credentials round trip first, doubling latency and — under any burst — giving the identity
 * provider twice the traffic at exactly the moment it is least able to absorb it.
 *
 * <p>The lock makes a refresh single-flight: many threads discovering an expired token at once
 * would otherwise stampede the token endpoint. Whoever holds the lock refreshes; everyone else
 * re-checks and finds a fresh token waiting.
 *
 * <p>The token is never logged and never leaves this class.
 */
class KeycloakServiceAccountTokens {

    private static final Logger log = LoggerFactory.getLogger(KeycloakServiceAccountTokens.class);

    /** Refresh this far ahead of expiry so a call in flight cannot be caught by the boundary. */
    private static final Duration SKEW = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    KeycloakServiceAccountTokens(RestClient restClient, KeycloakAdminProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    String get() {
        if (isFresh()) {
            return token;
        }
        lock.lock();
        try {
            if (isFresh()) {
                return token;
            }
            refresh();
            return token;
        } finally {
            lock.unlock();
        }
    }

    private boolean isFresh() {
        return token != null && Instant.now().isBefore(expiresAt.minus(SKEW));
    }

    private void refresh() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        try {
            Map<?, ?> body = restClient
                    .post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            if (body == null || !(body.get("access_token") instanceof String accessToken)) {
                throw new BusinessException(
                        IdentityErrorCode.IDENTITY_SYNC_FAILED, "The identity provider returned no access token");
            }
            long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 60L;
            this.token = accessToken;
            this.expiresAt = Instant.now().plusSeconds(expiresIn);
            log.debug("Refreshed identity-provider service-account token; valid for {}s", expiresIn);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // The cause goes to the log, never to the client: it can carry the provider's URL,
            // its version, and sometimes fragments of the request that failed.
            log.error("Failed to obtain a service-account token from the identity provider", ex);
            throw new BusinessException(
                    IdentityErrorCode.IDENTITY_SYNC_FAILED, "The identity provider is currently unavailable");
        }
    }

    /** Forces a refresh on the next call — used after a 401, in case the token was revoked early. */
    void invalidate() {
        this.expiresAt = Instant.EPOCH;
    }
}
