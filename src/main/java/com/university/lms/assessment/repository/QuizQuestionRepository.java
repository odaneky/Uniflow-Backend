package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.QuizQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByAssessmentIdOrderByPositionAsc(UUID assessmentId);

    long countByAssessmentId(UUID assessmentId);
}
