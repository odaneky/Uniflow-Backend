package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.QuizOption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {

    List<QuizOption> findByQuestionIdOrderByPositionAsc(UUID questionId);

    List<QuizOption> findByQuestion_Assessment_Id(UUID assessmentId);

    void deleteByQuestionId(UUID questionId);
}
