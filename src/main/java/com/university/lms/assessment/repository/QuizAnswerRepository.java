package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.QuizAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {

    List<QuizAnswer> findByAttemptId(UUID attemptId);

    Optional<QuizAnswer> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);

    @Query(
            """
            select a from QuizAnswer a
            join fetch a.question
            where a.attempt.id = :attemptId
            """)
    List<QuizAnswer> findByAttemptIdWithQuestion(@Param("attemptId") UUID attemptId);

    boolean existsByQuestionId(UUID questionId);
}
