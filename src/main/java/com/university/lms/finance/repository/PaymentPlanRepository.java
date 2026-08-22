package com.university.lms.finance.repository;

import com.university.lms.finance.domain.PaymentPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, UUID> {

    Optional<PaymentPlan> findByAcademicTermId(UUID academicTermId);
}
