package com.university.lms.support;

import com.university.lms.common.security.SecurityRoles;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Runs a block as an authenticated caller, for tests that invoke services directly rather than
 * through MockMvc.
 *
 * <p>Installs a real {@link JwtAuthenticationToken}, not a username/password one. The application
 * resolves the caller from the token's {@code sub} claim, so anything else fails with
 * {@code AUTHENTICATION_REQUIRED} — and a test helper that authenticates by a route production
 * never uses would be proving something the application does not do.
 *
 * <p>Matters most for multi-threaded tests: the security context is a {@link ThreadLocal} and is
 * <b>not</b> inherited by pool threads, so the token has to be installed inside the task itself.
 */
public final class RunAs {

    /**
     * One staff identity per JVM run: stable, so repeated calls resolve to the same provisioned
     * user, and unique per run because the integration database is reused between runs.
     */
    private static final String STAFF_SUBJECT = "test-staff-" + UUID.randomUUID();

    private RunAs() {}

    /** A staff caller — what a test exercising domain rules rather than ownership wants. */
    public static <T> T staff(Callable<T> action) throws Exception {
        return as(STAFF_SUBJECT, SecurityRoles.REGISTRAR, action);
    }

    public static <T> T as(String subject, String role, Callable<T> action) throws Exception {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(tokenFor(subject, role));
        try {
            return action.call();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private static JwtAuthenticationToken tokenFor(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject(subject)
                .claim("preferred_username", subject)
                .claim("email", subject + "@university.test")
                .claim("email_verified", true)
                .claim("given_name", "Test")
                .claim("family_name", "Caller")
                .build();

        List<GrantedAuthority> authorities = Arrays.stream(new String[] {role})
                .map(SecurityRoles::authority)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        return new JwtAuthenticationToken(jwt, authorities);
    }
}
