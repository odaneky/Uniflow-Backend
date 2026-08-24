package com.university.lms.reporting.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TermCensusResponse(
        UUID academicTermId,
        int headcount,
        BigDecimal averageTermGpa,
        int goodStandingCount,
        int probationCount,
        int creditsAttemptedTotal,
        int creditsEarnedTotal,
        List<ProgrammeCensusResponse> byProgramme) {

    public record ProgrammeCensusResponse(
            UUID programmeId, String programmeCode, String programmeName, int headcount, BigDecimal averageCumulativeGpa) {}
}
