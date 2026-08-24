package com.university.lms.student.dto;

import com.university.lms.student.domain.ProgrammeEnrolmentKind;
import com.university.lms.student.domain.StudentProgrammeEnrolment;
import java.time.LocalDate;
import java.util.UUID;

public record ProgrammeMembershipResponse(
        UUID id, UUID programmeId, ProgrammeEnrolmentKind kind, boolean primary, LocalDate startedOn, LocalDate endedOn) {

    public static ProgrammeMembershipResponse from(StudentProgrammeEnrolment membership) {
        return new ProgrammeMembershipResponse(
                membership.getId(),
                membership.getProgrammeId(),
                membership.getKind(),
                membership.isPrimary(),
                membership.getStartedOn(),
                membership.getEndedOn());
    }
}
