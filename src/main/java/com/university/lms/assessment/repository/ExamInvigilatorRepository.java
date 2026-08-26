package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.ExamInvigilator;
import com.university.lms.assessment.domain.ExamInvigilator.ExamInvigilatorId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamInvigilatorRepository extends JpaRepository<ExamInvigilator, ExamInvigilatorId> {

    List<ExamInvigilator> findByExamSittingIdOrderByAssignedAtAsc(UUID examSittingId);

    boolean existsByExamSittingIdAndUserId(UUID examSittingId, UUID userId);
}
