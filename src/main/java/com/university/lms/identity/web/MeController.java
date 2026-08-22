package com.university.lms.identity.web;

import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.dto.CurrentUserResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Who am I" — the entry point for any client holding a token and nothing else.
 *
 * <p>Deliberately outside {@code /users}, which is administrative: reading the roster is
 * privileged, reading yourself is not. Merging the two would mean either exposing the roster or
 * denying every ordinary user the one call they need before they can make any other.
 *
 * <p>Nothing here takes an identifier. The resource is determined entirely by the authenticated
 * principal, which is the property that makes the {@code /me} family safe: there is no parameter to
 * tamper with, so no version of {@code GET /students/{someone-else}} exists on this path.
 *
 * <p>The first request from an unknown external identity provisions the local row, so this is also
 * where a newly created identity becomes known to the application.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CurrentUserProvider currentUserProvider;

    public MeController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(
                currentUserProvider.require(), currentUserProvider.accountManagementUri().toString());
    }

    /**
     * Where to manage credentials.
     *
     * <p>A link, not an operation. Password changes, resets and multi-factor enrolment all belong to
     * the identity provider; UniFlow has no password to change and must never grow an endpoint that
     * implies otherwise.
     */
    @GetMapping("/security")
    public Map<String, Object> security() {
        return Map.of(
                "accountManagementUrl", currentUserProvider.accountManagementUri().toString(),
                "passwordManagedBy", "identity-provider",
                "message", "Password and multi-factor settings are managed by the university identity provider.");
    }
}
