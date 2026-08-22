package com.university.lms.academic.dto;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.academic.domain.TermType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A teaching period.
 *
 * <p>{@code registrationOpen} is evaluated server-side at the moment of the request rather than
 * left for the client to derive from the two timestamps — the term owns that rule.
 */
public record AcademicTermResponse(
        UUID id,
        UUID academicYearId,
        String name,
        TermType termType,
        int sequenceNumber,
        LocalDate startDate,
        LocalDate endDate,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        boolean registrationOpen,
        Instant addDropOpensAt,
        Instant addDropClosesAt,
        boolean addDropOpen,
        LocalDate tuitionDueOn,
        String phase) {

    public static AcademicTermResponse from(AcademicTerm term, Instant asOf) {
        return new AcademicTermResponse(
                term.getId(),
                term.getAcademicYear().getId(),
                term.getName(),
                term.getTermType(),
                term.getSequenceNumber(),
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
