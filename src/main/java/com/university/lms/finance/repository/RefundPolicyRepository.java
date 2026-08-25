package com.university.lms.finance.repository;

import com.university.lms.finance.domain.RefundPolicy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, UUID> {}
