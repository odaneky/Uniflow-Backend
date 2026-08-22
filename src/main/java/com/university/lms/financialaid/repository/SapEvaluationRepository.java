package com.university.lms.financialaid.repository;

import com.university.lms.financialaid.domain.SapEvaluation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SapEvaluationRepository extends JpaRepository<SapEvaluation, UUID> {

    Optional<SapEvaluation> findFirstByStudentIdAndAcademicTermIdOrderByEvaluatedAtDesc(
            UUID studentId, UUID academicTermId);

    List<SapEvaluation> findByStudentIdOrderByEvaluatedAtDesc(UUID studentId);
}
