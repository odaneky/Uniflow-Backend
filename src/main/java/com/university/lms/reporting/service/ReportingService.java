package com.university.lms.reporting.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.grading.api.TermCensus;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.reporting.dto.TermCensusResponse;
import com.university.lms.reporting.dto.TermCensusResponse.ProgrammeCensusResponse;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Institutional aggregates. Every report here reads from data a closed term or a completed process
 * already fixed — see {@link TermCensus} — never from live, still-mutable history, so re-running a
 * report against the same closed term always gives the same answer.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final TermCensus termCensus;
    private final StudentDirectory studentDirectory;
    private final AcademicStructure academicStructure;
    private final CurrentUserProvider currentUserProvider;

    public ReportingService(
            TermCensus termCensus,
            StudentDirectory studentDirectory,
            AcademicStructure academicStructure,
            CurrentUserProvider currentUserProvider) {
        this.termCensus = termCensus;
        this.studentDirectory = studentDirectory;
        this.academicStructure = academicStructure;
        this.currentUserProvider = currentUserProvider;
    }

    public TermCensusResponse termCensus(UUID academicTermId) {
        requireRegistry();
        if (academicStructure.findTerm(academicTermId, Instant.now()).isEmpty()) {
            throw new ResourceNotFoundException(
                    CommonErrorCode.RESOURCE_NOT_FOUND, "No academic term exists with id " + academicTermId);
        }
        TermCensus.Summary summary = termCensus.summarize(academicTermId);

        Map<UUID, List<TermCensus.PerStudentStanding>> byProgramme = new LinkedHashMap<>();
        for (TermCensus.PerStudentStanding student : summary.students()) {
            studentDirectory
                    .findById(student.studentId())
                    .ifPresent(record -> byProgramme
                            .computeIfAbsent(record.programmeId(), id -> new ArrayList<>())
                            .add(student));
        }

        List<ProgrammeCensusResponse> byProgrammeResponse = new ArrayList<>();
        for (Map.Entry<UUID, List<TermCensus.PerStudentStanding>> entry : byProgramme.entrySet()) {
            AcademicStructure.ProgrammeSummary programme =
                    academicStructure.findProgramme(entry.getKey()).orElse(null);
            byProgrammeResponse.add(new ProgrammeCensusResponse(
                    entry.getKey(),
                    programme == null ? null : programme.code(),
                    programme == null ? null : programme.name(),
                    entry.getValue().size(),
                    averageOf(entry.getValue())));
        }

        return new TermCensusResponse(
                summary.academicTermId(),
                summary.headcount(),
                summary.averageTermGpa(),
                summary.goodStandingCount(),
                summary.probationCount(),
                summary.creditsAttemptedTotal(),
                summary.creditsEarnedTotal(),
                byProgrammeResponse);
    }

    private static BigDecimal averageOf(List<TermCensus.PerStudentStanding> students) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (TermCensus.PerStudentStanding student : students) {
            if (student.cumulativeGpa() != null) {
                sum = sum.add(student.cumulativeGpa());
                count++;
            }
        }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "You do not have permission to view reports");
        }
    }
}
