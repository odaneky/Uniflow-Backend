package com.university.lms.academic.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * The academic module's published contract.
 *
 * <p>Student, course and enrolment all need to ask questions about programmes, departments and
 * terms; none of them may reach into {@code academic.repository} to do it. Note that the
 * registration-window rule is answered here rather than exported as raw dates — the caller asks
 * "is registration open?" and the owning module keeps the right to decide what that means.
 */
public interface AcademicStructure {

    record ProgrammeSummary(
            UUID id,
            String code,
            String name,
            String degreeAward,
            int totalCredits,
            boolean active,
            String programmeType,
            java.math.BigDecimal minGraduationGpa) {}

    /** Effective semester load after applying a programme override, if any. */
    record CreditLoad(int minSemesterCredits, int maxSemesterCredits, boolean programmeOverride) {}

    record TermSummary(
            UUID id, String name, UUID academicYearId, String academicYearCode, boolean registrationOpen) {}

    record TermCalendar(
            UUID id,
            String name,
            UUID academicYearId,
            String academicYearCode,
            LocalDate startDate,
            LocalDate endDate,
            Instant registrationOpensAt,
            Instant registrationClosesAt,
            boolean registrationOpen,
            Instant addDropOpensAt,
            Instant addDropClosesAt,
            boolean addDropOpen,
            LocalDate tuitionDueOn,
            String phase) {}

    boolean departmentExists(UUID departmentId);

    boolean programmeExists(UUID programmeId);

    Optional<ProgrammeSummary> findProgramme(UUID programmeId);

    Optional<TermSummary> findTerm(UUID termId, Instant asOf);

    /** False when the term does not exist or has no configured window. */
    boolean isRegistrationOpen(UUID termId, Instant asOf);

    /** Registration or add/drop — students may add a section. */
    boolean canAddEnrolment(UUID termId, Instant asOf);

    /** Drop without a transcript W. After this, the student must withdraw. */
    boolean canDropWithoutPenalty(UUID termId, Instant asOf);

    Optional<TermCalendar> findCalendar(UUID termId, Instant asOf);

    /** Prefers an open registration/add-drop term, otherwise the term in session today. */
    Optional<TermCalendar> currentTerm(Instant asOf);

    /** Institution default, or that programme's override when one is published. */
    CreditLoad creditLoadFor(UUID programmeId);

    /**
     * Hours after checkout during which a student may undo that cart, if registration or add/drop
     * is still open. {@code 0} disables the action.
     */
    int checkoutCorrectionHours();
}
