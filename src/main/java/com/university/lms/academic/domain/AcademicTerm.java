package com.university.lms.academic.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;

/**
 * A teaching period within an {@link AcademicYear}, e.g. {@code 2026/2027 Semester 1}.
 *
 * <p>Carries the registration window, because "may this student enrol right now?" is a question
 * about the term, not about the course section — putting it here keeps the rule in one place
 * instead of duplicated across every section.
 */
@Entity
@Table(
        name = "academic_terms",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_academic_terms_year_sequence",
                        columnNames = {"academic_year_id", "sequence_number"}),
        indexes = @Index(name = "idx_academic_terms_year", columnList = "academic_year_id"))
@Getter
public class AcademicTerm extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "academic_year_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_academic_terms_year"))
    private AcademicYear academicYear;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_type", nullable = false, length = 30)
    private TermType termType;

    /** Ordinal within the year; 1 for the first term. */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "registration_opens_at")
    private Instant registrationOpensAt;

    @Column(name = "registration_closes_at")
    private Instant registrationClosesAt;

    @Column(name = "add_drop_opens_at")
    private Instant addDropOpensAt;

    @Column(name = "add_drop_closes_at")
    private Instant addDropClosesAt;

    @Column(name = "tuition_due_on")
    private LocalDate tuitionDueOn;

    protected AcademicTerm() {
        // for JPA
    }

    public AcademicTerm(
            AcademicYear academicYear,
            String name,
            TermType termType,
            int sequenceNumber,
            LocalDate startDate,
            LocalDate endDate) {
        this.academicYear = academicYear;
        this.name = name;
        this.termType = termType;
        this.sequenceNumber = sequenceNumber;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void openRegistration(Instant opensAt, Instant closesAt) {
        this.registrationOpensAt = opensAt;
        this.registrationClosesAt = closesAt;
    }

    public void openAddDrop(Instant opensAt, Instant closesAt, LocalDate tuitionDueOn) {
        this.addDropOpensAt = opensAt;
        this.addDropClosesAt = closesAt;
        this.tuitionDueOn = tuitionDueOn;
    }

    /**
     * A term with no configured window is treated as closed: enrolment must be explicitly opened
     * rather than accidentally available because a field was left null.
     */
    public boolean isRegistrationOpenAt(Instant moment) {
        return registrationOpensAt != null
                && registrationClosesAt != null
                && !moment.isBefore(registrationOpensAt)
                && moment.isBefore(registrationClosesAt);
    }

    public boolean isAddDropOpenAt(Instant moment) {
        return addDropOpensAt != null
                && addDropClosesAt != null
                && !moment.isBefore(addDropOpensAt)
                && moment.isBefore(addDropClosesAt);
    }

    /** Students may add a section during priority registration or the add/drop period. */
    public boolean canAddAt(Instant moment) {
        return isRegistrationOpenAt(moment) || isAddDropOpenAt(moment);
    }

    /**
     * A drop (no transcript W) is allowed during add/drop, or during registration when the
     * registrar has not published a separate add/drop window.
     */
    public boolean canDropWithoutPenaltyAt(Instant moment) {
        if (addDropOpensAt != null && addDropClosesAt != null) {
            return isAddDropOpenAt(moment) || isRegistrationOpenAt(moment);
        }
        return isRegistrationOpenAt(moment);
    }

    public boolean isInSession(java.time.LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    public String phaseAt(Instant moment) {
        if (isAddDropOpenAt(moment)) {
            return "ADD_DROP";
        }
        if (isRegistrationOpenAt(moment)) {
            return "REGISTRATION";
        }
        if (isInSession(moment.atZone(java.time.ZoneOffset.UTC).toLocalDate())) {
            return "WITHDRAW_ONLY";
        }
        return "CLOSED";
    }
}
