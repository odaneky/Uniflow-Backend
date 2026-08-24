package com.university.lms.curriculum.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * A catalog year's edition of a programme's curriculum: {@code DRAFT}, then {@code PUBLISHED},
 * then eventually {@code RETIRED}.
 *
 * <p>Requirement blocks belong to exactly one version, and {@link #isEditable()} is what makes a
 * published version's blocks immutable — the service layer refuses a write once this returns
 * false, so a degree audit resolved against a bound version keeps returning the answer it would
 * have returned at the time, even after the programme's requirements are later revised.
 *
 * <p>Deliberately no fork-on-edit here yet: this pass only needs "a published version cannot
 * change," not the workflow that produces its replacement. Refusing the edit outright is the safer
 * failure mode until that workflow exists.
 */
@Entity
@Table(
        name = "curriculum_versions",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_curriculum_versions", columnNames = {"programme_id", "catalog_year"}),
        indexes = @Index(name = "idx_curriculum_versions_programme", columnList = "programme_id"))
@Getter
public class CurriculumVersion extends BaseEntity {

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "catalog_year", nullable = false, length = 9)
    private String catalogYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CurriculumVersionStatus status;

    /** Null until a later pass wires per-version credit/GPA overrides; {@code programmes} still governs. */
    @Column(name = "total_credits")
    private Integer totalCredits;

    @Column(name = "min_gpa", precision = 3, scale = 2)
    private BigDecimal minGpa;

    @Column(name = "residency_credits")
    private Integer residencyCredits;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected CurriculumVersion() {
        // for JPA
    }

    public CurriculumVersion(UUID programmeId, String catalogYear, LocalDate effectiveFrom) {
        this.programmeId = programmeId;
        this.catalogYear = catalogYear;
        this.status = CurriculumVersionStatus.DRAFT;
        this.effectiveFrom = effectiveFrom;
    }

    /** True only for {@code DRAFT} — the sole status whose requirement blocks may still be written. */
    public boolean isEditable() {
        return status == CurriculumVersionStatus.DRAFT;
    }

    public void publish() {
        if (status != CurriculumVersionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft curriculum version can be published");
        }
        this.status = CurriculumVersionStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void retire() {
        if (status != CurriculumVersionStatus.PUBLISHED) {
            throw new IllegalStateException("Only a published curriculum version can be retired");
        }
        this.status = CurriculumVersionStatus.RETIRED;
        this.retiredAt = Instant.now();
        this.effectiveTo = LocalDate.now();
    }
}
