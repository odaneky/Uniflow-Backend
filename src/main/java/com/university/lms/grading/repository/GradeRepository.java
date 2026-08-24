package com.university.lms.grading.repository;

import com.university.lms.grading.domain.Grade;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the grading module. */
public interface GradeRepository extends JpaRepository<Grade, UUID> {

    Page<Grade> findByStudentId(UUID studentId, Pageable pageable);

    /**
     * Published grades only.
     *
     * <p>Filtered in the query rather than after it: an unpublished row is never loaded, so no
     * later change to the mapping or the response DTO can accidentally expose a provisional mark.
     */
    Page<Grade> findByStudentIdAndPublishedTrue(UUID studentId, Pageable pageable);

    List<Grade> findAllByStudentIdAndPublishedTrue(UUID studentId);

    List<Grade> findByCourseSectionId(UUID courseSectionId);

    List<Grade> findByStudentIdAndCourseSectionIdAndPublishedTrue(UUID studentId, UUID courseSectionId);

    Optional<Grade> findByStudentIdAndCourseSectionIdAndAssessmentId(
            UUID studentId, UUID courseSectionId, UUID assessmentId);

    Optional<Grade> findByStudentIdAndCourseSectionIdAndAssessmentIdIsNull(UUID studentId, UUID courseSectionId);

    /** Overall (non-assessment) published results for a term — what term close locks and totals. */
    List<Grade> findByAcademicTermIdAndAssessmentIdIsNullAndPublishedTrue(UUID academicTermId);
}
