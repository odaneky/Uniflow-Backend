package com.university.lms.grading.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkGradeUploadRequest(
        @NotEmpty(message = "must contain at least one row")
                @Size(max = 500, message = "must contain at most 500 rows in one upload")
                @Valid
                List<CreateGradeRequest> grades,
        boolean dryRun) {}
