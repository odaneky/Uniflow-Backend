package com.university.lms.common.audit;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Supplies the {@code created_by} / {@code updated_by} value from the security context.
 *
 * <p>Reading the authenticated principal rather than anything request-scoped means auditing also
 * works for writes that originate from a scheduled job or a message consumer, where no HTTP
 * request exists.
 */
public class AuditorAwareImpl implements AuditorAware<String> {

    /** Recorded for writes with no authenticated principal, e.g. migrations and bootstrap tasks. */
    static final String SYSTEM_PRINCIPAL = "system";

    private static final String ANONYMOUS_PRINCIPAL = "anonymousUser";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(SYSTEM_PRINCIPAL);
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || ANONYMOUS_PRINCIPAL.equals(name)) {
            return Optional.of(SYSTEM_PRINCIPAL);
        }
        return Optional.of(name);
    }
}
