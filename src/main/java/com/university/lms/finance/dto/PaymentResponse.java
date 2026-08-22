package com.university.lms.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID entryId, BigDecimal amount, BigDecimal balance, String currency, Instant occurredAt) {}
