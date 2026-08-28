package com.university.lms.request.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A student-filed registry request with type-specific payload and workflow state.
 *
 * <p>Deliverable bytes live in the document module; this row tracks status and references.
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "decision_note", length = 2000)
    private String decisionNote;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "deliverable_document_id")
    private UUID deliverableDocumentId;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "fulfillment_error", length = 500)
    private String fulfillmentError;

    /** D9: when this request is expected to be decided by — {@link ServiceRequestType#slaDays()} from submission. */
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "escalated_by")
    private UUID escalatedBy;

    @Column(name = "escalation_reason", length = 1000)
    private String escalationReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ServiceRequest() {
        // for JPA
    }

    public ServiceRequest(
            UUID studentId,
            ServiceRequestType requestType,
            String reference,
            String note,
            String payloadJson,
            UUID assignedTo,
            Instant dueAt) {
        this.studentId = studentId;
        this.requestType = requestType;
        this.reference = reference;
        this.note = note;
        this.payload = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson;
        this.assignedTo = assignedTo;
        this.dueAt = dueAt;
    }

    public void transitionTo(ServiceRequestStatus target, UUID actorUserId, String transitionNote, Instant at) {
        this.status = target;
        if (target == ServiceRequestStatus.APPROVED
                || target == ServiceRequestStatus.DENIED
                || target == ServiceRequestStatus.CANCELLED
                || target == ServiceRequestStatus.COMPLETED
                || target == ServiceRequestStatus.IN_REVIEW) {
            this.decidedBy = actorUserId;
            this.decidedAt = at;
            if (transitionNote != null && !transitionNote.isBlank()) {
                this.decisionNote = transitionNote;
            }
        }
    }

    public void assignTo(UUID userId) {
        this.assignedTo = userId;
    }

    public void attachDeliverable(UUID documentId) {
        this.deliverableDocumentId = documentId;
    }

    public void markFulfilled(Instant at) {
        this.fulfilledAt = at;
        this.fulfillmentError = null;
    }

    public void markFulfillmentFailed(String error) {
        this.fulfillmentError = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 500));
    }

    /**
     * D9: flags this request for supervisory attention. Idempotent by design, not a one-shot
     * toggle — re-escalating an already-escalated request simply updates who raised it, when, and
     * why, so a second, more urgent reason is never silently dropped. Refused only once the request
     * is closed, since there is nothing left to triage.
     */
    public void escalate(UUID by, String reason, Instant at) {
        if (status.terminal()) {
            throw new IllegalStateException("A closed request cannot be escalated");
        }
        this.escalatedAt = at;
        this.escalatedBy = by;
        this.escalationReason = reason;
    }

    /** @deprecated legacy decide path — prefer {@link #transitionTo}. */
    @Deprecated
    public void decide(ServiceRequestStatus status, UUID decidedBy, String decisionNote, Instant at) {
        transitionTo(status, decidedBy, decisionNote, at);
    }
}
