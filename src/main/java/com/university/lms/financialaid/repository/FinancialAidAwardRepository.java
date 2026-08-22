package com.university.lms.financialaid.repository;

import com.university.lms.financialaid.domain.AwardStatus;
import com.university.lms.financialaid.domain.FinancialAidAward;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAidAwardRepository extends JpaRepository<FinancialAidAward, UUID> {

    List<FinancialAidAward> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<FinancialAidAward> findByStudentIdAndAcademicTermIdOrderByCreatedAtDesc(UUID studentId, UUID academicTermId);

    List<FinancialAidAward> findByStudentIdAndStatus(UUID studentId, AwardStatus status);
}
