package com.university.lms.admissions.dto;

import com.university.lms.admissions.domain.ApplicationFormFieldType;
import com.university.lms.admissions.domain.ApplicationFormSection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ApplicationFormFieldBody(
        @NotBlank(message = "is required") @Size(max = 60, message = "must be at most 60 characters") String key,
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String label,
        @NotNull(message = "is required") ApplicationFormFieldType type,
        @NotNull(message = "is required") ApplicationFormSection section,
        Boolean required,
        @Size(max = 200, message = "must be at most 200 characters") String placeholder,
        @Size(max = 500, message = "must be at most 500 characters") String helpText,
        Integer sortOrder,
        @Valid List<ApplicationFormOptionBody> options) {

    public record ApplicationFormOptionBody(
            @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters")
                    String value,
            @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters")
                    String label) {}
}
