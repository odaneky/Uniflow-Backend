package com.university.lms.finance.dto;

import com.university.lms.finance.api.PaymentStanding;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID studentId,
        String currency,
        BigDecimal balance,
        LocalDate dueOn,
        List<AccountEntryResponse> entries,
        PaymentStanding standing) {}
