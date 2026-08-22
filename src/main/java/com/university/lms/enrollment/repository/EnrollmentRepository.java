package com.university.lms.enrollment.repository;

import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Internal to the enrolment module. */
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByStudentIdAndCourseSectionId(UUID studentId, UUID courseSectionId);

    boolean existsByStudentIdAndCourseSectionIdAndStatusIn(
            UUID studentId, UUID courseSectionId, java.util.Collection<EnrollmentStatus> statuses);

    java.util.List<Enrollment> findByStudentIdAndCourseSectionIdAndStatusIn(
            UUID studentId, UUID courseSectionId, java.util.Collection<EnrollmentStatus> statuses);

    long countByCourseSectionIdAndStatusIn(UUID courseSectionId, java.util.Collection<EnrollmentStatus> statuses);

    java.util.List<Enrollment> findByCourseSectionIdAndStatusIn(
            UUID courseSectionId, java.util.Collection<EnrollmentStatus> statuses);

    java.util.List<Enrollment> findByCourseSectionIdAndStatusOrderByEnrolledAtAsc(
            UUID courseSectionId, EnrollmentStatus status);

    java.util.List<Enrollment> findByStudentIdAndStatusIn(
            UUID studentId, java.util.Collection<EnrollmentStatus> statuses);

    java.util.List<Enrollment> findByStudentIdAndCheckoutBatchId(UUID studentId, UUID checkoutBatchId);

    @Query(
            """
            select coalesce(max(e.attemptNumber), 0) from Enrollment e
            where e.studentId = :studentId
              and e.courseSectionId in :sectionIds
              and e.status in :statuses
            """)
    int maxAttemptNumberForSections(
            UUID studentId,
            java.util.Collection<UUID> sectionIds,
            java.util.Collection<EnrollmentStatus> statuses);

    @Query(
            """
            select e from Enrollment e
            where e.studentId = :studentId
              and e.courseSectionId = :courseSectionId
            order by e.enrolledAt desc
            """)
    java.util.List<Enrollment> findAllByStudentIdAndCourseSectionIdOrderByEnrolledAtDesc(
            UUID studentId, UUID courseSectionId);

    @Query(
            """
            select e from Enrollment e
            where (:studentId is null or e.studentId = :studentId)
              and (:courseSectionId is null or e.courseSectionId = :courseSectionId)
              and (:status is null or e.status = :status)
            """)
    Page<Enrollment> search(UUID studentId, UUID courseSectionId, EnrollmentStatus status, Pageable pageable);
}
