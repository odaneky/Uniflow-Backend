package com.university.lms.academic.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.academic.api.AcademicStructure.CreditLoad;
import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.academic.repository.AcademicTermRepository;
import com.university.lms.academic.repository.DepartmentRepository;
import com.university.lms.academic.repository.ProgrammeRepository;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts the academic module's internals to its published {@link AcademicStructure} contract. */
@Service
@Transactional(readOnly = true)
public class DefaultAcademicStructure implements AcademicStructure {

    private final DepartmentRepository departmentRepository;
    private final ProgrammeRepository programmeRepository;
    private final AcademicTermRepository academicTermRepository;
    private final AcademicPolicyService academicPolicyService;

    public DefaultAcademicStructure(
            DepartmentRepository departmentRepository,
            ProgrammeRepository programmeRepository,
            AcademicTermRepository academicTermRepository,
            AcademicPolicyService academicPolicyService) {
        this.departmentRepository = departmentRepository;
        this.programmeRepository = programmeRepository;
        this.academicTermRepository = academicTermRepository;
        this.academicPolicyService = academicPolicyService;
    }


    @Override
    public Optional<ExamPeriod> examPeriod(UUID termId, LocalDate on) {
        return academicTermRepository
                .findById(termId)
                .filter(term -> term.getExamStartsOn() != null && term.getExamEndsOn() != null)
                .map(term -> new ExamPeriod(term.getExamStartsOn(), term.getExamEndsOn(), term.inExamPeriod(on)));
    }

    @Override
    public boolean departmentExists(UUID departmentId) {
        return departmentId != null && departmentRepository.existsById(departmentId);
    }

    @Override
    public boolean programmeExists(UUID programmeId) {
        return programmeId != null && programmeRepository.existsById(programmeId);
    }

    @Override
    public Optional<ProgrammeSummary> findProgramme(UUID programmeId) {
        if (programmeId == null) {
            return Optional.empty();
        }
        return programmeRepository
                .findById(programmeId)
                .map(programme -> new ProgrammeSummary(
                        programme.getId(),
                        programme.getCode(),
                        programme.getName(),
                        programme.getDegreeAward(),
                        programme.getTotalCredits(),
                        programme.isActive(),
                        programme.getProgrammeType().name(),
                        programme.getMinGraduationGpa()));
    }

    @Override
    public Optional<TermSummary> findTerm(UUID termId, Instant asOf) {
        if (termId == null) {
            return Optional.empty();
        }
        return academicTermRepository
                .findById(termId)
                .map(term -> new TermSummary(
                        term.getId(),
                        term.getName(),
                        term.getAcademicYear().getId(),
                        term.getAcademicYear().getCode(),
                        term.isRegistrationOpenAt(asOf)));
    }

    @Override
    public boolean isRegistrationOpen(UUID termId, Instant asOf) {
        return findTerm(termId, asOf).map(TermSummary::registrationOpen).orElse(false);
    }

    @Override
    public boolean canAddEnrolment(UUID termId, Instant asOf) {
        return academicTermRepository.findById(termId).map(term -> term.canAddAt(asOf)).orElse(false);
    }

    @Override
    public boolean canDropWithoutPenalty(UUID termId, Instant asOf) {
        return academicTermRepository.findById(termId).map(term -> term.canDropWithoutPenaltyAt(asOf)).orElse(false);
    }

    @Override
    public Optional<TermCalendar> findCalendar(UUID termId, Instant asOf) {
        if (termId == null) {
            return Optional.empty();
        }
        return academicTermRepository.findById(termId).map(term -> toCalendar(term, asOf));
    }

    @Override
    public Optional<TermCalendar> currentTerm(Instant asOf) {
        LocalDate today = asOf.atZone(ZoneOffset.UTC).toLocalDate();
        return academicTermRepository.findAll().stream()
                .filter(term -> term.canAddAt(asOf) || term.isInSession(today))
                .max(Comparator.comparing((AcademicTerm term) -> term.canAddAt(asOf))
                        .thenComparing(AcademicTerm::getStartDate))
                .map(term -> toCalendar(term, asOf));
    }

    @Override
    public CreditLoad creditLoadFor(UUID programmeId) {
        return academicPolicyService.creditLoadFor(programmeId);
    }

    @Override
    public int checkoutCorrectionHours() {
        return academicPolicyService.institutionPolicy().checkoutCorrectionHours();
    }

    @Override
    public int termOrdinal(UUID termId) {
        AcademicTerm term = academicTermRepository
                .findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "No academic term exists with id " + termId));
        return (int) academicTermRepository.countUpToAndIncluding(term.getStartDate(), term.getSequenceNumber());
    }

    @Override
    public Optional<UUID> departmentOfProgramme(UUID programmeId) {
        if (programmeId == null) {
            return Optional.empty();
        }
        return programmeRepository.findById(programmeId).map(programme -> programme.getDepartment().getId());
    }

    private static TermCalendar toCalendar(AcademicTerm term, Instant asOf) {
        return new TermCalendar(
                term.getId(),
                term.getName(),
                term.getAcademicYear().getId(),
                term.getAcademicYear().getCode(),
                term.getStartDate(),
                term.getEndDate(),
                term.getRegistrationOpensAt(),
                term.getRegistrationClosesAt(),
                term.isRegistrationOpenAt(asOf),
                term.getAddDropOpensAt(),
                term.getAddDropClosesAt(),
                term.isAddDropOpenAt(asOf),
                term.getTuitionDueOn(),
                term.phaseAt(asOf));
    }
}
