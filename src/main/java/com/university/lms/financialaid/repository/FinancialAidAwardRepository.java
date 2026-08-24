package com.university.lms.financialaid.repository;

import com.university.lms.financialaid.domain.AwardStatus;
import com.university.lms.financialaid.domain.AwardType;
import com.university.lms.financialaid.domain.FinancialAidAward;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAidAwardRepository extends JpaRepository<FinancialAidAward, UUID> {

    List<FinancialAidAward> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<FinancialAidAward> findByStudentIdAndAcademicTermIdOrderByCreatedAtDesc(UUID studentId, UUID academicTermId);

    List<FinancialAidAward> findByStudentIdAndStatus(UUID studentId, AwardStatus status);

    /** Backs the idempotent path in {@code packageAwards}: at most one award per student, per
     * term, per type — {@code uk_financial_aid_awards_student_term_type} is the guarantee this
     * courtesy check exists to avoid tripping under normal (non-concurrent) retry. */
    Optional<FinancialAidAward> findByStudentIdAndAcademicTermIdAndAwardType(
            UUID studentId, UUID academicTermId, AwardType awardType);
}
