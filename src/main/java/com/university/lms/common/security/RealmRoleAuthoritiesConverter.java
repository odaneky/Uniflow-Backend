package com.university.lms.common.security;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns the token's roles into Spring Security authorities.
 *
 * <p>Without this, authorization silently fails closed. Spring's default converter reads the
 * {@code scope}/{@code scp} claim and emits {@code SCOPE_*} authorities; Keycloak puts roles in a
 * nested {@code realm_access.roles} claim and emits no scopes for them. The result is a token that
 * is perfectly valid, an authenticated principal with an empty authority list, and every
 * {@code hasRole} check returning 403 — with nothing in the logs suggesting a mapping problem.
 *
 * <p>The path is configured rather than fixed (see {@link ClaimMappingProperties}), so federating
 * the realm to another identity provider is a configuration change.
 *
 * <p>Identity-provider housekeeping roles — {@code default-roles-<realm>}, {@code offline_access},
 * {@code uma_authorization} — are mapped through unchanged. They grant nothing, because an
 * authority only matters when a rule names it and no rule here names those.
 */
public final class RealmRoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final TokenClaimReader claims;

    public RealmRoleAuthoritiesConverter(TokenClaimReader claims) {
        this.claims = claims;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return claims.roles(jwt).stream()
                .map(SecurityRoles::authority)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }
}
