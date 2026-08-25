package com.university.lms.grading.dto;

import java.util.List;
import java.util.UUID;

public record BulkGradeUploadResponse(
        boolean dryRun, int rowsConsidered, int succeeded, int failed, List<Row> rows) {

    public record Row(int index, UUID studentId, UUID courseSectionId, String outcome, String detail) {}
}
