package com.university.lms.admissions.dto;

import java.util.List;
import java.util.UUID;

public record ProgrammeApplicationFormResponse(
        UUID programmeId,
        String programmeCode,
        String programmeName,
        boolean customized,
        List<ApplicationFormFieldResponse> fields) {}
