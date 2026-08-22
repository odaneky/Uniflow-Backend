package com.university.lms.request.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A student-filed registry request.
 *
 * <p>Listing and status are the product for v1 — a transcript request does not generate a PDF
 * here. Document bytes stay in the object store via the document module.
 */
@Entity
@Table(
        name = "service_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_service_requests_reference", columnNames = "reference"),
        indexes = {
            @Index(name = "idx_service_requests_student", columnList = "student_id"),
            @Index(name = "idx_service_requests_status", columnList = "status")
        })
@Getter
public class ServiceRequest extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private ServiceRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ServiceRequestStatus status = ServiceRequestStatus.SUBMITTED;

    @Column(name = "reference", nullable = false, length = 20)
    private String reference;

    @Column(name = "note", length = 2000)
    private String note;

    @Column(name = "decision_note", length = 2000)
    private String decisionNote;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ServiceRequest() {
        // for JPA
    }

    public ServiceRequest(UUID studentId, ServiceRequestType requestType, String reference, String note) {
        this.studentId = studentId;
        this.requestType = requestType;
        this.reference = reference;
        this.note = note;
    }

    public void decide(ServiceRequestStatus status, UUID decidedBy, String decisionNote, Instant at) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.decidedAt = at;
    }
}
