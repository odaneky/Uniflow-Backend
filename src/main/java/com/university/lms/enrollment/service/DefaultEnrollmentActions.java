package com.university.lms.enrollment.service;

import com.university.lms.enrollment.api.EnrollmentActions;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultEnrollmentActions implements EnrollmentActions {

    private static final Set<EnrollmentStatus> WITHDRAWABLE =
            EnumSet.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING);

    private final EnrollmentRepository repository;
    private final EnrollmentService enrollmentService;

    public DefaultEnrollmentActions(EnrollmentRepository repository, EnrollmentService enrollmentService) {
        this.repository = repository;
        this.enrollmentService = enrollmentService;
    }

    @Override
    public boolean canWithdraw(UUID enrollmentId, UUID studentId) {
        return repository
                .findById(enrollmentId)
                .filter(row -> row.getStudentId().equals(studentId))
                .map(row -> WITHDRAWABLE.contains(row.getStatus()))
                .orElse(false);
    }

    @Override
    @Transactional
    public void withdraw(UUID enrollmentId, UUID actorUserId) {
        enrollmentService.withdraw(enrollmentId);
    }

    @Override
    @Transactional
    public void lateAdd(UUID studentId, UUID courseSectionId, UUID actorUserId) {
        enrollmentService.lateAdd(studentId, courseSectionId);
    }
}
