package com.university.lms.admissions.domain;

import java.util.List;

/** One configurable question on a programme application form. */
public record ApplicationFormFieldDefinition(
        String key,
        String label,
        ApplicationFormFieldType type,
        ApplicationFormSection section,
        boolean required,
        String placeholder,
        String helpText,
        int sortOrder,
        List<ApplicationFormOptionDefinition> options) {

    public record ApplicationFormOptionDefinition(String value, String label) {}
}
