package com.university.lms.financialaid.dto;

import com.university.lms.financialaid.domain.HoldType;
import com.university.lms.financialaid.domain.ServiceHold;
import java.time.Instant;
import java.util.UUID;

public record ServiceHoldResponse(
        UUID id,
        UUID studentId,
        HoldType holdType,
        String reason,
        boolean active,
        Instant placedAt,
        Instant clearedAt,
        UUID placedBy) {

    public static ServiceHoldResponse from(ServiceHold hold) {
        return new ServiceHoldResponse(
                hold.getId(),
                hold.getStudentId(),
                hold.getHoldType(),
                hold.getReason(),
                hold.isActive(),
                hold.getPlacedAt(),
                hold.getClearedAt(),
                hold.getPlacedBy());
    }
}
