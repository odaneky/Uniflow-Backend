package com.university.lms.identity.integration.keycloak;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.identity.domain.IdentityErrorCode;
import com.university.lms.identity.spi.IdentityAccount;
import com.university.lms.identity.spi.IdentityProvider;
import com.university.lms.identity.spi.NewIdentityAccount;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The one class that knows Keycloak's administrative REST API exists.
 *
 * <p>Errors are translated, never forwarded. A Keycloak response body can contain the realm's
 * internal identifiers, the provider's version, and occasionally an echo of the request; passing
 * any of that to an API client hands out a free map of the identity estate. Everything here becomes
 * a stable {@link IdentityErrorCode} with the detail confined to the log.
 */
class KeycloakIdentityProvider implements IdentityProvider {

    private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityProvider.class);

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;
    private final KeycloakServiceAccountTokens tokens;

    KeycloakIdentityProvider(
            RestClient restClient, KeycloakAdminProperties properties, KeycloakServiceAccountTokens tokens) {
        this.restClient = restClient;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<IdentityAccount> findBySubject(String subject) {
        return call("find account", () -> {
            Map<?, ?> user = get("/users/" + enc(subject), Map.class);
            return user == null ? Optional.empty() : Optional.of(KeycloakUserMapper.toAccount(user, roles(subject)));
        });
    }

    @Override
    public Optional<IdentityAccount> findByUsername(String username) {
        return call("find account by username", () -> {
            // exact=true matters: without it Keycloak does a prefix/infix search, and "2020123"
            // would match a different student. An identity lookup must never be fuzzy.
            List<?> found = get("/users?exact=true&username=" + enc(username), List.class);
            if (found == null || found.isEmpty() || !(found.get(0) instanceof Map<?, ?> user)) {
                return Optional.empty();
            }
            String subject = user.get("id") instanceof String id ? id : null;
            return Optional.of(KeycloakUserMapper.toAccount(user, subject == null ? Set.of() : roles(subject)));
        });
    }

    @Override
    public IdentityAccount createAccount(NewIdentityAccount request) {
        return call("create account", () -> {
            try {
                restClient
                        .post()
                        .uri(properties.adminRealmUri() + "/users")
                        .headers(h -> h.setBearerAuth(tokens.get()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(KeycloakUserMapper.toRepresentation(request))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 409) {
                    throw new ResourceAlreadyExistsException(
                            IdentityErrorCode.USERNAME_ALREADY_EXISTS,
                            "An identity already exists for " + request.username());
                }
                throw ex;
            }

            // Keycloak answers a create with a Location header, not a body. Reading the account
            // back is also the only way to learn the subject it assigned, which is the identifier
            // everything downstream keys on.
            IdentityAccount created = findByUsername(request.username())
                    .orElseThrow(() -> new BusinessException(
                            IdentityErrorCode.IDENTITY_SYNC_FAILED,
                            "The account was created but could not be read back"));

            request.realmRoles().forEach(role -> grantRealmRole(created.subject(), role));
            return new IdentityAccount(
                    created.subject(),
                    created.username(),
                    created.email(),
                    created.firstName(),
                    created.lastName(),
                    created.enabled(),
                    created.studentNumber(),
                    request.realmRoles());
        });
    }

    @Override
    public void setEnabled(String subject, boolean enabled) {
        call("set account enabled", () -> {
            // A targeted PUT of one field. A full representation PUT would silently reset anything
            // the caller did not send — including required actions and administratively owned
            // attributes such as the student number.
            restClient
                    .put()
                    .uri(properties.adminRealmUri() + "/users/" + enc(subject))
                    .headers(h -> h.setBearerAuth(tokens.get()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", enabled))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public Set<String> realmRoles(String subject) {
        return call("read realm roles", () -> roles(subject));
    }

    @Override
    public java.util.List<String> subjectsWithRealmRole(String role) {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return call("list accounts with role", () -> {
            List<?> users = get("/roles/" + enc(role) + "/users?max=200", List.class);
            if (users == null || users.isEmpty()) {
                return List.of();
            }
            return users.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(user -> user.get("id"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        });
    }

    @Override
    public void grantRealmRole(String subject, String role) {
        call("grant realm role", () -> {
            restClient
                    .post()
                    .uri(properties.adminRealmUri() + "/users/" + enc(subject) + "/role-mappings/realm")
                    .headers(h -> h.setBearerAuth(tokens.get()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(requireRealmRole(role)))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public void revokeRealmRole(String subject, String role) {
        call("revoke realm role", () -> {
            restClient
                    .method(org.springframework.http.HttpMethod.DELETE)
                    .uri(properties.adminRealmUri() + "/users/" + enc(subject) + "/role-mappings/realm")
                    .headers(h -> h.setBearerAuth(tokens.get()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(requireRealmRole(role)))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public URI accountManagementUri() {
        return URI.create(properties.accountUri());
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * The realm-role representation needed to assign or remove a mapping.
     *
     * <p>Read from the role <em>list</em> rather than from {@code GET /roles/{name}}, which requires
     * {@code view-realm} — a much broader privilege than this service account should hold. Listing
     * is permitted by the roles it already has, so filtering client-side buys the same result
     * without widening the blast radius of a leaked service-account secret.
     *
     * <p>The realm has a handful of roles, so this is a small response and an exact-match scan.
     */
    private Map<?, ?> requireRealmRole(String role) {
        List<?> all = get("/roles", List.class);
        if (all != null) {
            for (Object candidate : all) {
                if (candidate instanceof Map<?, ?> representation && role.equals(representation.get("name"))) {
                    return Map.of("id", representation.get("id"), "name", representation.get("name"));
                }
            }
        }
        throw new com.university.lms.common.exception.ResourceNotFoundException(
                IdentityErrorCode.ROLE_NOT_FOUND, "No role exists named " + role);
    }

    private Set<String> roles(String subject) {
        List<?> assigned = get("/users/" + enc(subject) + "/role-mappings/realm", List.class);
        if (assigned == null) {
            return Set.of();
        }
        return assigned.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(role -> role.get("name"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toUnmodifiableSet());
    }

    private <T> T get(String path, Class<T> type) {
        return restClient
                .get()
                .uri(properties.adminRealmUri() + path)
                .headers(h -> h.setBearerAuth(tokens.get()))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    // 404 is a legitimate answer to "does this account exist"; let it become null
                    // rather than an exception the caller has to unwrap.
                    if (response.getStatusCode().value() != 404) {
                        throw new RestClientResponseException(
                                "Identity provider rejected the request",
                                response.getStatusCode().value(),
                                response.getStatusText(),
                                response.getHeaders(),
                                null,
                                null);
                    }
                })
                .body(type);
    }

    /**
     * Runs an administrative call, translating every failure into the application's vocabulary.
     *
     * <p>A 401 invalidates the cached token before failing, so a revoked or rotated service-account
     * credential heals on the next attempt instead of wedging until a restart.
     */
    private <T> T call(String description, java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (com.university.lms.common.exception.ApplicationException ex) {
            // Already in the application's vocabulary — a translated 409, a missing role. Pass through.
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                tokens.invalidate();
            }
            log.error("Identity provider call failed: {} (HTTP {})", description, ex.getStatusCode().value(), ex);
            throw new BusinessException(
                    IdentityErrorCode.IDENTITY_SYNC_FAILED, "The identity provider rejected an administrative request");
        } catch (RuntimeException ex) {
            log.error("Identity provider call failed: {}", description, ex);
            throw new BusinessException(
                    IdentityErrorCode.IDENTITY_SYNC_FAILED, "The identity provider is currently unavailable");
        }
    }

    private static String enc(String segment) {
        return java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8);
    }
}
