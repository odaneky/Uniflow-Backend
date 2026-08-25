package com.university.lms.admissions.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.admissions.domain.ApplicationStatus;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A6: {@code assertStaffAction} widened to also accept {@code ADMISSIONS_OFFICER}, alongside
 * {@code REGISTRAR} — never instead of it, since nobody has been granted the narrower role in any
 * real environment yet.
 */
class AdmissionsWorkflowTest {

    private final AdmissionsWorkflow workflow = new AdmissionsWorkflow();

    private static CurrentUser callerWithRole(String role) {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(role), Set.of());
    }

    @Test
    @DisplayName("REGISTRAR keeps admissions access, unchanged")
    void registrarIsStillAuthorized() {
        assertThatCode(() -> workflow.assertStaffAction(callerWithRole(SecurityRoles.REGISTRAR), ApplicationStatus.ADMITTED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMISSIONS_OFFICER is additionally authorized, alongside REGISTRAR")
    void admissionsOfficerIsAuthorized() {
        assertThatCode(() -> workflow.assertStaffAction(
                        callerWithRole(SecurityRoles.ADMISSIONS_OFFICER), ApplicationStatus.ADMITTED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a role with no admissions stake is still refused")
    void unrelatedRoleIsRefused() {
        assertThatThrownBy(() -> workflow.assertStaffAction(callerWithRole(SecurityRoles.LECTURER), ApplicationStatus.ADMITTED))
                .isInstanceOf(ForbiddenException.class);
    }
}
