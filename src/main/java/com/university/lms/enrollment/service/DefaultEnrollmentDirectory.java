package com.university.lms.enrollment.service;

import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.course.api.CourseCatalog;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts enrolment internals to the published {@link EnrollmentDirectory} contract. */
@Service
@Transactional(readOnly = true)
public class DefaultEnrollmentDirectory implements EnrollmentDirectory {

    private static final Set<EnrollmentStatus> LEARNING_ACCESS =
            Set.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.COMPLETED);

    private static final Set<EnrollmentStatus> ROSTER = Set.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING);

    private static final Set<EnrollmentStatus> OCCUPYING_SEATS =
            Set.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.PENDING);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseCatalog courseCatalog;

    public DefaultEnrollmentDirectory(EnrollmentRepository enrollmentRepository, CourseCatalog courseCatalog) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseCatalog = courseCatalog;
    }

    @Override
    public boolean canAccessLearning(UUID studentId, UUID courseSectionId) {
        if (studentId == null || courseSectionId == null) {
            return false;
        }
        return enrollmentRepository
                .findByStudentIdAndCourseSectionId(studentId, courseSectionId)
                .map(Enrollment::getStatus)
                .filter(LEARNING_ACCESS::contains)
                .isPresent();
    }

    @Override
    public List<SectionEnrolment> rosterOf(UUID courseSectionId) {
        if (courseSectionId == null) {
            return List.of();
        }
        return enrollmentRepository.findByCourseSectionIdAndStatusIn(courseSectionId, ROSTER).stream()
                .map(this::toEnrolment)
                .toList();
    }

    @Override
    public int occupyingSeatCount(UUID courseSectionId) {
        if (courseSectionId == null) {
            return 0;
        }
        return (int) enrollmentRepository.countByCourseSectionIdAndStatusIn(courseSectionId, OCCUPYING_SEATS);
    }

    @Override
    @Transactional
    public void reconcileSeatCount(UUID courseSectionId) {
        if (courseSectionId == null) {
            return;
        }
        int actual = occupyingSeatCount(courseSectionId);
        CourseCatalog.SectionSummary section = courseCatalog.findSection(courseSectionId).orElse(null);
        if (section == null) {
            return;
        }
        int capped = Math.min(actual, Math.max(section.capacity(), 0));
        if (section.enrolledCount() != capped) {
            courseCatalog.replaceEnrolledCount(courseSectionId, capped);
        }
    }

    @Override
    public List<UUID> accessibleSectionIds(UUID studentId) {
        if (studentId == null) {
            return List.of();
        }
        return enrollmentRepository.findByStudentIdAndStatusIn(studentId, LEARNING_ACCESS).stream()
                .map(Enrollment::getCourseSectionId)
                .toList();
    }

    private SectionEnrolment toEnrolment(Enrollment enrolment) {
        return new SectionEnrolment(
                enrolment.getId(),
                enrolment.getStudentId(),
                enrolment.getCourseSectionId(),
                enrolment.getStatus().name());
    }
}
