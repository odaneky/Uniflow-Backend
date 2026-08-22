package com.university.lms.enrollment.repository;

import com.university.lms.enrollment.domain.EnrollmentCheckoutIdempotency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentCheckoutIdempotencyRepository
        extends JpaRepository<EnrollmentCheckoutIdempotency, UUID> {

    Optional<EnrollmentCheckoutIdempotency> findByStudentIdAndIdempotencyKey(
            UUID studentId, String idempotencyKey);
}
