package com.university.lms.admissions.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An applicant's programme application through the admissions workflow. */
@Entity
@Table(
        name = "applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_applications_reference", columnNames = "reference"),
        indexes = {
            @Index(name = "idx_applications_status", columnList = "status"),
            @Index(name = "idx_applications_programme", columnList = "programme_id"),
            @Index(name = "idx_applications_term", columnList = "academic_term_id"),
            @Index(name = "idx_applications_email", columnList = "applicant_email")
        })
@Getter
public class Application extends BaseEntity {

    @Column(name = "applicant_email", nullable = false, length = 255)
    private String applicantEmail;

    @Column(name = "applicant_name", nullable = false, length = 200)
    private String applicantName;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    @Column(name = "reference", nullable = false, length = 20)
    private String reference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "deposit_amount", precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "deposit_paid_at")
    private Instant depositPaidAt;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "decision_note", length = 2000)
    private String decisionNote;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Application() {
        // for JPA
    }

    public Application(
            String applicantEmail,
            String applicantName,
            UUID programmeId,
            UUID academicTermId,
            String reference,
            String payloadJson) {
        this.applicantEmail = applicantEmail.trim().toLowerCase();
        this.applicantName = applicantName.trim();
        this.programmeId = programmeId;
        this.academicTermId = academicTermId;
        this.reference = reference;
        this.payload = blankPayloadToNull(payloadJson);
    }

    public void updateDraft(String applicantEmail, String applicantName, String payloadJson) {
        if (applicantEmail != null && !applicantEmail.isBlank()) {
            this.applicantEmail = applicantEmail.trim().toLowerCase();
        }
        if (applicantName != null && !applicantName.isBlank()) {
            this.applicantName = applicantName.trim();
        }
        if (payloadJson != null) {
            this.payload = blankPayloadToNull(payloadJson);
        }
    }

    public void transitionTo(ApplicationStatus target, UUID actorUserId, String note, Instant at) {
        this.status = target;
        if (target == ApplicationStatus.SUBMITTED) {
            this.submittedAt = at;
        }
        if (target == ApplicationStatus.IN_REVIEW
                || target == ApplicationStatus.ADMITTED
                || target == ApplicationStatus.DENIED
                || target == ApplicationStatus.WAITLISTED) {
            this.decidedBy = actorUserId;
            this.decidedAt = at;
            if (note != null && !note.isBlank()) {
                this.decisionNote = note;
            }
        }
        if (target == ApplicationStatus.MATRICULATED) {
            this.decidedBy = actorUserId;
            this.decidedAt = at;
        }
    }

    public void assignTo(UUID userId) {
        this.assignedTo = userId;
    }

    public void setDepositAmount(BigDecimal amount) {
        this.depositAmount = amount;
    }

    public void recordDepositPaid(Instant at) {
        this.depositPaidAt = at;
    }

    public void linkStudent(UUID studentId) {
        this.studentId = studentId;
    }

    private static String blankPayloadToNull(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank() || "{}".equals(payloadJson.trim())) {
            return null;
        }
        return payloadJson;
    }
}
