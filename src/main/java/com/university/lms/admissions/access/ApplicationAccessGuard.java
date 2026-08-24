package com.university.lms.admissions.access;

import com.university.lms.admissions.domain.Application;
import com.university.lms.admissions.domain.AdmissionsErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.identity.api.CurrentUserProvider;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides whether a caller may act on a particular application.
 *
 * <p>Two legitimate callers, and only two:
 *
 * <ul>
 *   <li><b>Staff</b>, identified by a bearer token and a role. They work the admissions queue and
 *       need no capability token.
 *   <li><b>The applicant</b>, holding the capability token issued for that one application.
 * </ul>
 *
 * <p>Anyone else is refused — including someone holding only the application id, which used to be
 * sufficient on its own.
 *
 * <p>Every refusal is the same {@code 403}, whatever the reason: no token, wrong token, expired
 * token, or an application that does not exist. Distinguishing them would tell an anonymous caller
 * which ids are real, and turn the token into an oracle for probing.
 */
@Component
public class ApplicationAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(ApplicationAccessGuard.class);

    private final CurrentUserProvider currentUserProvider;

    public ApplicationAccessGuard(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * True when the caller is signed-in staff; they bypass the capability token entirely.
     *
     * <p>Read from the token rather than from a local user row. Staff acting for the first time have
     * no row yet, and a check that required one would refuse them — authorization must not depend on
     * whether somebody happens to have signed in before.
     */
    public boolean isStaff() {
        return currentUserProvider.isStaffCaller();
    }

    /**
     * @param presentedToken the {@code X-Application-Token} header, or null
     * @throws ForbiddenException when the caller is neither staff nor the holder of a valid token
     */
    public void requireAccess(Application application, String presentedToken) {
        if (isStaff()) {
            return;
        }
        if (presentedToken == null || presentedToken.isBlank()) {
            throw refuse(application, "no application token presented");
        }
        if (!ApplicationAccessToken.matches(presentedToken, application.getAccessTokenHash())) {
            throw refuse(application, "application token did not match");
        }
        if (application.accessTokenExpired(Instant.now())) {
            // Expiry is what makes a leaked link stop mattering. Resuming issues a fresh one.
            throw refuse(application, "application token has expired");
        }
    }

    private ForbiddenException refuse(Application application, String reason) {
        // The reason is logged, never returned: an anonymous caller learns only that they may not.
        log.warn("Refused access to application {}: {}", application.getId(), reason);
        return new ForbiddenException(
                AdmissionsErrorCode.APPLICATION_ACCESS_DENIED,
                "You do not have access to this application. Use the link emailed to you, or start a new application.");
    }
}
