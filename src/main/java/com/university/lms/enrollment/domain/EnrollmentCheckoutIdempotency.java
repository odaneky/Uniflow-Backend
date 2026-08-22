package com.university.lms.enrollment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Stores checkout responses keyed by Idempotency-Key header for safe client retries. */
@Entity
@Table(name = "enrollment_checkout_idempotency")
@Getter
public class EnrollmentCheckoutIdempotency {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "checkout_batch_id", nullable = false)
    private UUID checkoutBatchId;

    @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected EnrollmentCheckoutIdempotency() {
        // for JPA
    }

    public EnrollmentCheckoutIdempotency(
            UUID studentId, String idempotencyKey, UUID checkoutBatchId, String responseJson) {
        this.studentId = studentId;
        this.idempotencyKey = idempotencyKey;
        this.checkoutBatchId = checkoutBatchId;
        this.responseJson = responseJson;
    }
}
