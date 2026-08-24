package com.university.lms.admissions.dto;

import com.university.lms.admissions.domain.ApplicationFormFieldDefinition;
import com.university.lms.admissions.domain.ApplicationFormFieldType;
import com.university.lms.admissions.domain.ApplicationFormSection;
import java.util.List;
import java.util.UUID;

public record ApplicationFormFieldResponse(
        String key,
        String label,
        ApplicationFormFieldType type,
        ApplicationFormSection section,
        boolean required,
        String placeholder,
        String helpText,
        int sortOrder,
        List<ApplicationFormOptionResponse> options) {

    public record ApplicationFormOptionResponse(String value, String label) {}

    public static ApplicationFormFieldResponse from(ApplicationFormFieldDefinition field) {
        List<ApplicationFormOptionResponse> options = field.options() == null
                ? List.of()
                : field.options().stream()
                        .map(option -> new ApplicationFormOptionResponse(option.value(), option.label()))
                        .toList();
        return new ApplicationFormFieldResponse(
                field.key(),
                field.label(),
                field.type(),
                field.section(),
                field.required(),
                field.placeholder(),
                field.helpText(),
                field.sortOrder(),
                options);
    }
}
