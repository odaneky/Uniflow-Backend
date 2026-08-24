package com.university.lms.admissions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceProgrammeApplicationFormRequest(@NotNull @Valid List<ApplicationFormFieldBody> fields) {}
