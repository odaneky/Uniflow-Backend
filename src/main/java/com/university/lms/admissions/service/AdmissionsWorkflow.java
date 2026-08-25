package com.university.lms.admissions.service;

import com.university.lms.admissions.domain.AdmissionDecision;
import com.university.lms.admissions.domain.AdmissionsErrorCode;
import com.university.lms.admissions.domain.Application;
import com.university.lms.admissions.domain.ApplicationStatus;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Enforced transitions and role gates for admissions workflow. */
@Component
public class AdmissionsWorkflow {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = Map.of(
            ApplicationStatus.DRAFT, EnumSet.of(ApplicationStatus.SUBMITTED),
            ApplicationStatus.SUBMITTED,
                    EnumSet.of(ApplicationStatus.IN_REVIEW, ApplicationStatus.DENIED),
            ApplicationStatus.IN_REVIEW,
                    EnumSet.of(
                            ApplicationStatus.ADMITTED,
                            ApplicationStatus.DENIED,
                            ApplicationStatus.WAITLISTED),
            ApplicationStatus.WAITLISTED,
                    EnumSet.of(ApplicationStatus.ADMITTED, ApplicationStatus.DENIED),
            ApplicationStatus.ADMITTED, EnumSet.of(ApplicationStatus.MATRICULATED));

    public void assertTransition(Application application, ApplicationStatus target) {
        Set<ApplicationStatus> allowed = TRANSITIONS.getOrDefault(application.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_INVALID_TRANSITION,
                    "Cannot move from " + application.getStatus() + " to " + target);
        }
    }

    /**
     * A6 groundwork: widened to also accept {@code ADMISSIONS_OFFICER}, the eventual owner of this
     * area. {@code REGISTRAR} keeps everything it has today — nobody has been granted {@code
     * ADMISSIONS_OFFICER} in any real environment yet, so narrowing this to exclude {@code
     * REGISTRAR} would lock out every real registrar the day it shipped.
     */
    public void assertStaffAction(CurrentUser caller, ApplicationStatus target) {
        if (!caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                && !caller.hasRole(SecurityRoles.REGISTRAR)
                && !caller.hasRole(SecurityRoles.ADMISSIONS_OFFICER)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to manage admissions");
        }
        if (target == ApplicationStatus.DENIED || target == ApplicationStatus.WAITLISTED) {
            return;
        }
        if (target == ApplicationStatus.IN_REVIEW
                || target == ApplicationStatus.ADMITTED
                || target == ApplicationStatus.MATRICULATED) {
            return;
        }
        throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "Action not permitted");
    }

    public ApplicationStatus targetFor(AdmissionDecision decision) {
        return switch (decision) {
            case ADMIT -> ApplicationStatus.ADMITTED;
            case DENY -> ApplicationStatus.DENIED;
            case WAITLIST -> ApplicationStatus.WAITLISTED;
        };
    }
}
