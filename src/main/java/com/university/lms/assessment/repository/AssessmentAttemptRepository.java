package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.AssessmentAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the assessment module. */
public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, UUID> {

    List<AssessmentAttempt> findByAssessmentIdAndStudentIdOrderByAttemptNumberAsc(UUID assessmentId, UUID studentId);

    Optional<AssessmentAttempt> findByAssessmentIdAndStudentIdAndAttemptNumber(
            UUID assessmentId, UUID studentId, int attemptNumber);

    @Query("select a from AssessmentAttempt a join fetch a.assessment where a.assessment.id = :assessmentId order by a.attemptNumber asc")
    List<AssessmentAttempt> findByAssessmentIdWithAssessment(@Param("assessmentId") UUID assessmentId);

    @Query("select a from AssessmentAttempt a join fetch a.assessment where a.id = :id")
    Optional<AssessmentAttempt> findByIdWithAssessment(@Param("id") UUID id);
}
