package com.university.lms.financialaid.dto;

import com.university.lms.financialaid.domain.IsirSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IsirSnapshotResponse(
        UUID id,
        UUID studentId,
        String aidYear,
        BigDecimal efc,
        boolean pellEligible,
        Instant importedAt) {

    public static IsirSnapshotResponse from(IsirSnapshot snapshot) {
        return new IsirSnapshotResponse(
                snapshot.getId(),
                snapshot.getStudentId(),
                snapshot.getAidYear(),
                snapshot.getEfc(),
                snapshot.isPellEligible(),
                snapshot.getImportedAt());
    }
}
