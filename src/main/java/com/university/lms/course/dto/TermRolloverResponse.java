package com.university.lms.course.dto;

import java.util.List;
import java.util.UUID;

public record TermRolloverResponse(
        UUID sourceTermId,
        UUID targetTermId,
        boolean dryRun,
        int sectionsConsidered,
        int created,
        int skipped,
        int failed,
        List<Row> rows) {

    public record Row(
            String courseCode, String sourceSectionCode, String newSectionCode, String outcome, String detail) {}
}
