package com.university.lms.student.service;

import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.student.domain.ProgrammeEnrolmentEndReason;
import com.university.lms.student.domain.ProgrammeEnrolmentKind;
import com.university.lms.student.domain.StudentErrorCode;
import com.university.lms.student.domain.StudentProgrammeEnrolment;
import com.university.lms.student.repository.StudentProgrammeEnrolmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of {@code student_programme_enrolments}.
 *
 * <p>{@code students.programme_id} is still what the rest of the system reads — dropping it is a
 * separate, later pass, since dozens of files across four modules consume it through {@code
 * StudentDirectory.StudentSummary}. This service's job for now is narrower: keep a dated record of
 * programme membership alongside that field, and keep the two from drifting apart by writing both
 * in the same transaction as the caller that changes one.
 *
 * <p>Deliberately has no dependency on {@code curriculum}: that module already depends on {@code
 * student} (for {@code StudentDirectory}), so this module taking a dependency back on it would be
 * the module graph's first cycle. {@code curriculumVersionId} is written {@code null} here and left
 * for {@code curriculum} to populate later through a write path published from this module's own
 * {@code api} — the dependency can only run the one way it already does.
 */
@Service
@Transactional(readOnly = true)
public class StudentProgrammeEnrolmentService {

    private final StudentProgrammeEnrolmentRepository repository;

    public StudentProgrammeEnrolmentService(StudentProgrammeEnrolmentRepository repository) {
        this.repository = repository;
    }

    /** Opens a student's first programme membership — called once, from academic provisioning. */
    @Transactional
    public void openInitial(UUID studentId, UUID programmeId, LocalDate startedOn) {
        open(studentId, programmeId, startedOn);
    }

    /**
     * Closes the open primary membership (if one exists) and opens a new one. Called alongside
     * {@code Student.transferToProgramme} so the two never disagree about the student's current
     * programme.
     */
    @Transactional
    public void transfer(UUID studentId, UUID newProgrammeId, LocalDate effectiveOn, String reason, UUID approvedBy) {
        // Closing the old row must physically flush before the new row is inserted: Hibernate's
        // default action-queue order runs inserts before updates, so without this the insert would
        // reach the database first and collide with uk_spe_open_primary against the still-open row.
        repository
                .findByStudentIdAndEndedOnIsNullAndPrimaryTrue(studentId)
                .ifPresent(open -> {
                    open.end(effectiveOn, ProgrammeEnrolmentEndReason.TRANSFERRED, reason, approvedBy);
                    repository.saveAndFlush(open);
                });
        open(studentId, newProgrammeId, effectiveOn);
    }

    /** The programme of the student's current, open primary membership, if one has ever been opened. */
    public Optional<UUID> currentProgrammeId(UUID studentId) {
        return repository
                .findByStudentIdAndEndedOnIsNullAndPrimaryTrue(studentId)
                .map(StudentProgrammeEnrolment::getProgrammeId);
    }

    /** Every open membership — the primary major alongside any minors, specialisations or double majors. */
    public List<StudentProgrammeEnrolment> openMembershipsOf(UUID studentId) {
        return repository.findByStudentIdAndEndedOnIsNullOrderByStartedOnAsc(studentId);
    }

    /**
     * Adds a minor, specialisation, or a second major — never the primary membership, which is only
     * ever set by {@link #openInitial} and {@link #transfer}.
     */
    @Transactional
    public StudentProgrammeEnrolment addSecondary(
            UUID studentId, UUID programmeId, ProgrammeEnrolmentKind kind, LocalDate startedOn) {
        if (repository.existsByStudentIdAndProgrammeIdAndKindAndEndedOnIsNull(studentId, programmeId, kind)) {
            throw new ResourceAlreadyExistsException(
                    StudentErrorCode.PROGRAMME_MEMBERSHIP_ALREADY_OPEN,
                    "This student already has an open " + kind + " membership in that programme");
        }
        return repository.save(
                new StudentProgrammeEnrolment(studentId, programmeId, null, kind, false, startedOn));
    }

    /** Ends one secondary membership. The primary membership can only be ended by {@link #transfer}. */
    @Transactional
    public void endSecondary(
            UUID enrolmentId, LocalDate endedOn, ProgrammeEnrolmentEndReason endReason, String reason, UUID approvedBy) {
        StudentProgrammeEnrolment membership = repository
                .findById(enrolmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.PROGRAMME_MEMBERSHIP_NOT_FOUND,
                        "No programme membership exists with id " + enrolmentId));
        if (membership.isPrimary()) {
            throw new ValidationException(
                    StudentErrorCode.PROGRAMME_MEMBERSHIP_IS_PRIMARY,
                    "The primary programme membership can only be ended by transferring to a new one");
        }
        membership.end(endedOn, endReason, reason, approvedBy);
        repository.save(membership);
    }

    private void open(UUID studentId, UUID programmeId, LocalDate startedOn) {
        repository.save(
                new StudentProgrammeEnrolment(studentId, programmeId, null, ProgrammeEnrolmentKind.MAJOR, true, startedOn));
    }
}
