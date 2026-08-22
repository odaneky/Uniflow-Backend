package com.university.lms.financialaid.dto;

import java.util.List;

public record IsirImportResponse(int imported, int updated, int skipped, List<IsirSnapshotResponse> snapshots) {}
