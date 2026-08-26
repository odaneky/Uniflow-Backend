package com.university.lms.request.service;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.grading.api.GradeDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.student.api.StudentDirectory;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Enforced transitions and role gates per request type. */
@Component
public class ServiceRequestWorkflow {

    /** What every request type gets unless {@link #TYPE_TRANSITIONS} overrides it for that type. */
    private static final Map<ServiceRequestStatus, Set<ServiceRequestStatus>> DEFAULT_TRANSITIONS = Map.of(
            ServiceRequestStatus.SUBMITTED,
                    EnumSet.of(
                            ServiceRequestStatus.IN_REVIEW,
                            ServiceRequestStatus.DENIED,
                            ServiceRequestStatus.CANCELLED),
            ServiceRequestStatus.IN_REVIEW,
                    EnumSet.of(ServiceRequestStatus.APPROVED, ServiceRequestStatus.DENIED),
            ServiceRequestStatus.APPROVED, EnumSet.of(ServiceRequestStatus.COMPLETED));

    /**
     * D3: {@code DEFAULT_TRANSITIONS} used to be the only graph, applied identically to every
     * type — so a type needing an extra step (a second sign-off, say) could only get one by adding
     * a status to {@link ServiceRequestStatus} and deciding what it means for every other type too,
     * since nothing here read {@code request_type} at all. This map is the seam that removes that
     * coupling: empty today because no type needs to diverge yet, but a future type can override its
     * own graph here — reusing whatever new {@code ServiceRequestStatus} values it needs — while
     * every other type keeps reading {@code DEFAULT_TRANSITIONS} completely unchanged.
     */
    private static final Map<ServiceRequestType, Map<ServiceRequestStatus, Set<ServiceRequestStatus>>>
            TYPE_TRANSITIONS = Map.of();

    private final StudentDirectory studentDirectory;
    private final CourseCatalog courseCatalog;
    private final GradeDirectory gradeDirectory;

    public ServiceRequestWorkflow(
            StudentDirectory studentDirectory, CourseCatalog courseCatalog, GradeDirectory gradeDirectory) {
        this.studentDirectory = studentDirectory;
        this.courseCatalog = courseCatalog;
        this.gradeDirectory = gradeDirectory;
    }

    public void assertTransition(ServiceRequest request, ServiceRequestStatus target) {
        Set<ServiceRequestStatus> allowed = transitionsFor(request.getRequestType())
                .getOrDefault(request.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_INVALID_TRANSITION,
                    "Cannot move from " + request.getStatus() + " to " + target);
        }
    }

    private static Map<ServiceRequestStatus, Set<ServiceRequestStatus>> transitionsFor(ServiceRequestType type) {
        return TYPE_TRANSITIONS.getOrDefault(type, DEFAULT_TRANSITIONS);
    }

    public void assertStaffAction(CurrentUser caller, ServiceRequest request, ServiceRequestStatus target) {
        if (target == ServiceRequestStatus.DENIED && request.getStatus() == ServiceRequestStatus.SUBMITTED) {
            assertCanReview(caller, request);
            return;
        }
        switch (target) {
            case IN_REVIEW -> assertCanReview(caller, request);
            case APPROVED, DENIED -> assertCanDecide(caller, request);
            case COMPLETED -> assertCanComplete(caller, request);
            default -> throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "Action not permitted");
        }
    }

    public void assertStudentCancel(CurrentUser caller, ServiceRequest request, UUID studentUserId) {
        if (request.getStatus() != ServiceRequestStatus.SUBMITTED) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_INVALID_TRANSITION, "Only submitted requests can be cancelled");
        }
        studentDirectory
                .studentIdOfUser(caller.userId())
                .filter(id -> id.equals(request.getStudentId()))
                .orElseThrow(() -> new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "Not your request"));
    }

    public UUID defaultAssignee(ServiceRequestType type, UUID studentId) {
        if (type != ServiceRequestType.WITHDRAWAL) {
            return null;
        }
        return studentDirectory
                .findById(studentId)
                .flatMap(student -> studentDirectory.advisorUserIdOf(student.userId()))
                .orElse(null);
    }

    private void assertCanReview(CurrentUser caller, ServiceRequest request) {
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return;
        }
        if (request.getRequestType() == ServiceRequestType.WITHDRAWAL
                && caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR)
                && isAdvisorOf(caller.userId(), request.getStudentId())) {
            return;
        }
        if (request.getRequestType() == ServiceRequestType.APPEAL && canReviewAppeal(caller, request)) {
            return;
        }
        // A6: ServiceRequestType.SAP_APPEAL.reviewStep() already documents "Financial Aid Review"
        // as the intended reviewer — this catches the check up to that existing, undisputed intent,
        // not a new mapping invented here. Additive: REGISTRAR still reviews every type, unchanged.
        if (request.getRequestType() == ServiceRequestType.SAP_APPEAL
                && caller.hasRole(SecurityRoles.FINANCIAL_AID_OFFICER)) {
            return;
        }
        throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "You cannot review this request");
    }

    private void assertCanDecide(CurrentUser caller, ServiceRequest request) {
        assertCanReview(caller, request);
    }

    private void assertCanComplete(CurrentUser caller, ServiceRequest request) {
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return;
        }
        if (request.getRequestType() == ServiceRequestType.WITHDRAWAL
                && caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR)
                && isAdvisorOf(caller.userId(), request.getStudentId())) {
            return;
        }
        if (request.getRequestType() == ServiceRequestType.SAP_APPEAL
                && caller.hasRole(SecurityRoles.FINANCIAL_AID_OFFICER)) {
            return;
        }
        throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "You cannot complete this request");
    }

    private boolean isAdvisorOf(UUID advisorUserId, UUID studentId) {
        return studentDirectory
                .findById(studentId)
                .map(student -> studentDirectory.adviseeUserIdsOf(advisorUserId).contains(student.userId()))
                .orElse(false);
    }

    private boolean canReviewAppeal(CurrentUser caller, ServiceRequest request) {
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return true;
        }
        UUID gradeId = ServiceRequestPayloads.gradeId(request.getPayload());
        if (gradeId == null) {
            return false;
        }
        return gradeDirectory
                .findById(gradeId)
                .map(grade -> caller.hasRole(SecurityRoles.LECTURER)
                        && courseCatalog.teaches(caller.userId(), grade.courseSectionId()))
                .orElse(false);
    }
}
