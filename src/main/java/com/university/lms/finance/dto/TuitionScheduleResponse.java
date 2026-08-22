package com.university.lms.finance.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TuitionScheduleResponse(
        BigDecimal amountPerCredit, BigDecimal campusFee, List<ProgrammeRate> programmeRates) {

    public record ProgrammeRate(UUID programmeId, BigDecimal amountPerCredit) {}
}
