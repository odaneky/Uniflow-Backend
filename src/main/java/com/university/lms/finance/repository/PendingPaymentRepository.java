package com.university.lms.finance.repository;

import com.university.lms.finance.domain.PendingPayment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingPaymentRepository extends JpaRepository<PendingPayment, UUID> {

    Optional<PendingPayment> findByProviderAndProviderReference(String provider, String providerReference);
}
