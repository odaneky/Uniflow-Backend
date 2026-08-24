package com.university.lms.identity.service;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.UnauthorizedException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.common.security.TokenClaimReader;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.domain.Permission;
import com.university.lms.identity.domain.Role;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.RoleRepository;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.identity.spi.IdentityProvider;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Resolves the bearer token in the security context to a local user row.
 *
 * <p>The result is cached for the duration of the request. Ownership checks fire more than once in a
 * single call — enrolling touches the caller's identity, and so does the response it builds — and
 * without the cache each one is another lookup, plus another chance to enter the provisioning path.
 * That path opens a second transaction, so on a caller's very first request it briefly holds two
 * connections; caching bounds that to once per request rather than once per check.
 */
@Service
@Transactional(readOnly = true)
public class DefaultCurrentUserProvider implements CurrentUserProvider {

    /**
     * A request attribute rather than a {@code @RequestScope} bean: no proxying, and it degrades to
     * a plain lookup outside an HTTP request, which is where scheduled work and tests live.
     */
    private static final String CACHE_KEY = DefaultCurrentUserProvider.class.getName() + ".currentUser";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserProvisioningService provisioningService;
    private final TokenClaimReader claims;
    private final IdentityProvider identityProvider;

    public DefaultCurrentUserProvider(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserProvisioningService provisioningService,
            TokenClaimReader claims,
            IdentityProvider identityProvider) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.provisioningService = provisioningService;
        this.claims = claims;
        this.identityProvider = identityProvider;
    }

    @Override
    public CurrentUser require() {
        CurrentUser cached = cached();
        if (cached != null) {
            return cached;
        }
        Jwt jwt = currentJwt()
                .orElseThrow(() -> new UnauthorizedException(
                        CommonErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required to access this resource"));

        String subject = claims.subject(jwt)
                .orElseThrow(() -> new UnauthorizedException(
                        CommonErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required to access this resource"));

        User user = userRepository
                .findByKeycloakSubject(subject)
                .orElseGet(() -> provisioningService.provision(jwt));

        CurrentUser resolved = toCurrentUser(user, jwt, subject);
        cache(resolved);
        return resolved;
    }

    @Override
    public Optional<CurrentUser> find() {
        CurrentUser cached = cached();
        if (cached != null) {
            return Optional.of(cached);
        }
        return currentJwt().flatMap(jwt -> claims.subject(jwt)
                .flatMap(userRepository::findByKeycloakSubject)
                .map(user -> toCurrentUser(user, jwt, claims.subject(jwt).orElseThrow())));
    }

    @Override
    public Set<String> callerRoles() {
        return currentRoles();
    }

    @Override
    public boolean isStaffCaller() {
        Set<String> roles = currentRoles();
        return roles.stream().anyMatch(role -> !SecurityRoles.STUDENT.equals(role));
    }

    @Override
    public URI accountManagementUri() {
        return identityProvider.accountManagementUri();
    }

    private CurrentUser toCurrentUser(User user, Jwt jwt, String subject) {
        Set<String> roles = currentRoles();
        return new CurrentUser(
                user.getId(),
                subject,
                user.getUsername(),
                user.getEmail(),
                user.fullName(),
                claims.studentNumber(jwt),
                roles,
                permissionsOf(roles));
    }

    /**
     * UniFlow's own permissions, resolved from the roles the token asserts.
     *
     * <p>The role names are the join. They are kept identical between the realm and
     * {@code SecurityRoles} on purpose, so this lookup cannot silently miss.
     */
    private Set<String> permissionsOf(Set<String> roles) {
        Set<String> permissions = new LinkedHashSet<>();
        for (String role : roles) {
            roleRepository
                    .findByNameWithPermissions(role)
                    .map(Role::getPermissions)
                    .ifPresent(granted -> granted.stream().map(Permission::getName).forEach(permissions::add));
        }
        return Set.copyOf(permissions);
    }

    /**
     * Roles come from the token's authorities, stripped of Spring's {@code ROLE_} prefix.
     *
     * <p>Never from a local table. The identity provider is authoritative for what someone may do;
     * reading a local copy as well would create a second answer free to drift from the first.
     */
    private static Set<String> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Only a real bearer token carries roles. Spring installs an AnonymousAuthenticationToken on
        // permitAll routes, complete with a ROLE_ANONYMOUS authority — and a naive "any role that is
        // not STUDENT means staff" test reads that as staff, handing every anonymous caller
        // administrative access to the public endpoints. Requiring a JWT is what closes it.
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(SecurityRoles.ROLE_PREFIX))
                .map(authority -> authority.substring(SecurityRoles.ROLE_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }

    private static CurrentUser cached() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes == null
                ? null
                : (CurrentUser) attributes.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
    }

    private static void cache(CurrentUser user) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(CACHE_KEY, user, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
