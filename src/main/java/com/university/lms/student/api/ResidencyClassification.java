package com.university.lms.student.api;

/**
 * The three-tier residency classification a community college uses to set tuition. Defaults to
 * {@link #IN_DISTRICT} for every student unless admissions or the registry says otherwise.
 */
public enum ResidencyClassification {
    IN_DISTRICT,
    OUT_OF_DISTRICT,
    OUT_OF_STATE
}
