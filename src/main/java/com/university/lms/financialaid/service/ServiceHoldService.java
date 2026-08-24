package com.university.lms.financialaid.service;

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

    @Transactional
    public ServiceHoldResponse placeHold(UUID studentId, HoldType holdType, String reason) {
        requireRegistry();
        CurrentUser actor = currentUserProvider.require();
        return ServiceHoldResponse.from(placeHoldInternal(studentId, holdType, reason, actor.userId()));
    }

    @Transactional
    ServiceHold placeHoldInternal(UUID studentId, HoldType holdType, String reason, UUID placedBy) {
        requireStudentExists(studentId);
        return repository.save(new ServiceHold(studentId, holdType, reason, Instant.now(), placedBy));
    }

    @Transactional
    public ServiceHoldResponse clearHold(UUID holdId) {
        requireRegistry();
        ServiceHold hold = repository
                .findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinancialAidErrorCode.HOLD_NOT_FOUND, "No service hold exists with id " + holdId));
        try {
            hold.clear(Instant.now());
        } catch (IllegalStateException ex) {
            throw new BusinessException(FinancialAidErrorCode.HOLD_ALREADY_CLEARED, ex.getMessage());
        }
        return ServiceHoldResponse.from(hold);
    }

    @Override
    @Transactional
    public void clearSapHold(UUID studentId) {
        clearActiveHoldsOfType(studentId, HoldType.SAP);
    }

    /** Clears all active holds of a type — e.g. SAP appeal waiver. */
    @Transactional
    public void clearActiveHoldsOfType(UUID studentId, HoldType holdType) {
        requireRegistry();
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

    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }
}
