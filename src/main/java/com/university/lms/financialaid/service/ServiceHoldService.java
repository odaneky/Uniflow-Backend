package com.university.lms.financialaid.service;

import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.financialaid.api.HoldActions;
import com.university.lms.financialaid.domain.FinancialAidErrorCode;
import com.university.lms.financialaid.domain.HoldType;
import com.university.lms.financialaid.domain.ServiceHold;
import com.university.lms.financialaid.dto.ServiceHoldResponse;
import com.university.lms.financialaid.repository.ServiceHoldRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceHoldService implements HoldActions {

    private final ServiceHoldRepository repository;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;

    public ServiceHoldService(
            ServiceHoldRepository repository,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.studentDirectory = studentDirectory;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ServiceHoldResponse> activeHoldsFor(UUID studentId) {
        requireStudentExists(studentId);
        return repository.findByStudentIdAndActiveTrueOrderByPlacedAtDesc(studentId).stream()
                .map(ServiceHoldResponse::from)
                .toList();
    }

    public List<ServiceHoldResponse> allHoldsFor(UUID studentId) {
        requireRegistry();
        requireStudentExists(studentId);
        return repository.findByStudentIdOrderByPlacedAtDesc(studentId).stream()
                .map(ServiceHoldResponse::from)
                .toList();
    }

    @Auditable(
            action = AuditTrail.Action.SERVICE_HOLD_PLACED,
            entityType = AuditTrail.EntityType.SERVICE_HOLD,
            entityId = "#result.id()",
            details = "#holdType.name() + ': ' + #reason")
    @Transactional
    public ServiceHoldResponse placeHold(UUID studentId, HoldType holdType, String reason) {
        CurrentUser actor = requireAuthorizedForHoldType(holdType);
        return ServiceHoldResponse.from(placeHoldInternal(studentId, holdType, reason, actor.userId()));
    }

    /**
     * Also called directly by {@code SapService} through this injected {@code ServiceHoldService}
     * reference — a genuinely separate external entry point from {@link #placeHold}'s controller
     * path, not a self-invocation, so it needs its own {@code @Auditable} rather than relying on
     * {@code placeHold}'s.
     */
    @Auditable(
            action = AuditTrail.Action.SERVICE_HOLD_PLACED,
            entityType = AuditTrail.EntityType.SERVICE_HOLD,
            entityId = "#result.getId()",
            details = "#holdType.name() + ': ' + #reason")
    @Transactional
    ServiceHold placeHoldInternal(UUID studentId, HoldType holdType, String reason, UUID placedBy) {
        requireStudentExists(studentId);
        return repository.save(new ServiceHold(studentId, holdType, reason, Instant.now(), placedBy));
    }

    @Auditable(
            action = AuditTrail.Action.SERVICE_HOLD_CLEARED,
            entityType = AuditTrail.EntityType.SERVICE_HOLD,
            entityId = "#holdId")
    @Transactional
    public ServiceHoldResponse clearHold(UUID holdId) {
        ServiceHold hold = repository
                .findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinancialAidErrorCode.HOLD_NOT_FOUND, "No service hold exists with id " + holdId));
        requireAuthorizedForHoldType(hold.getHoldType());
        try {
            hold.clear(Instant.now());
        } catch (IllegalStateException ex) {
            throw new BusinessException(FinancialAidErrorCode.HOLD_ALREADY_CLEARED, ex.getMessage());
        }
        return ServiceHoldResponse.from(hold);
    }

    // Clears every active SAP hold for the student, not one specific hold, so this is scoped to
    // the student rather than to any single ServiceHold row — the same reasoning
    // TuitionScheduleService uses for an institution-wide change with no one entity to point at,
    // except here a real entity (the student) is available and worth keeping the event
    // discoverable through, rather than leaving entityId null.
    @Auditable(
            action = AuditTrail.Action.SERVICE_HOLD_CLEARED,
            entityType = AuditTrail.EntityType.STUDENT,
            entityId = "#studentId",
            details = "'SAP holds cleared'")
    @Override
    @Transactional
    public void clearSapHold(UUID studentId) {
        clearActiveHoldsOfType(studentId, HoldType.SAP);
    }

    /** Clears all active holds of a type — e.g. SAP appeal waiver. */
    @Transactional
    public void clearActiveHoldsOfType(UUID studentId, HoldType holdType) {
        requireAuthorizedForHoldType(holdType);
        requireStudentExists(studentId);
        for (ServiceHold hold : repository.findByStudentIdAndActiveTrueOrderByPlacedAtDesc(studentId)) {
            if (hold.getHoldType() == holdType && hold.isActive()) {
                hold.clear(Instant.now());
            }
        }
    }

    List<ServiceHold> activeEntitiesFor(UUID studentId) {
        return repository.findByStudentIdAndActiveTrueOrderByPlacedAtDesc(studentId);
    }

    private void requireStudentExists(UUID studentId) {
        if (!studentDirectory.exists(studentId)) {
            throw new ResourceNotFoundException(
                    FinancialAidErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + studentId);
        }
    }

    /**
     * A6 groundwork: {@link #allHoldsFor} lists every hold type at once, so — like {@code
     * StudentService.search} and {@code EnrollmentService.search} before it — there is no single
     * type to narrow against; it stays registry-only.
     */
    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }

    /**
     * A6: {@code HoldType} spans several future owners — unlike the single-role widenings so far,
     * this dispatches by type rather than accepting one extra role unconditionally. {@code
     * REGISTRAR} keeps every type, unconditionally, exactly as before.
     *
     * <p>{@code FINANCIAL} additionally accepts {@code BURSAR}, {@code SAP} additionally accepts
     * {@code FINANCIAL_AID_OFFICER} — both scaffolding, inert until someone is actually granted the
     * new role in a real environment, same as every other A6 widening this session.
     *
     * <p>{@code ADVISING} additionally accepts {@code ACADEMIC_ADVISOR} — an <em>existing</em> role
     * with real holders already, so unlike the rest of this pass this one is not inert: an advisor
     * gains real, immediate ability to place and clear their own advisees' advising holds. Still
     * safe to ship — it only adds a capability nobody had before, never removes one.
     *
     * <p>{@code ORIENTATION}, {@code PLACEMENT} and {@code MANUAL} have no obvious narrower owner
     * and stay registry-only rather than a guess.
     */
    private CurrentUser requireAuthorizedForHoldType(HoldType holdType) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return caller;
        }
        String narrowerRole =
                switch (holdType) {
                    case FINANCIAL -> SecurityRoles.BURSAR;
                    case SAP -> SecurityRoles.FINANCIAL_AID_OFFICER;
                    case ADVISING -> SecurityRoles.ACADEMIC_ADVISOR;
                    case ORIENTATION, PLACEMENT, MANUAL -> null;
                };
        if (narrowerRole != null && caller.hasRole(narrowerRole)) {
            return caller;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
    }
}
