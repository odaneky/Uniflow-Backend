package com.university.lms.academic.domain;

import com.university.lms.academic.api.AcademicStructure.CreditLoad;
import com.university.lms.common.exception.ValidationException;

/** Resolves institution defaults against an optional programme override. */
public final class CreditLoadPolicy {

    public static final int MIN_ALLOWED = 1;
    public static final int MAX_ALLOWED = 40;
    public static final int DEFAULT_MIN = 12;
    public static final int DEFAULT_MAX = 18;

    private CreditLoadPolicy() {}

    public static CreditLoad resolve(int institutionMin, int institutionMax, Integer programmeMin, Integer programmeMax) {
        requireRange("institution minimum", institutionMin);
        requireRange("institution maximum", institutionMax);
        if (institutionMin > institutionMax) {
            throw new ValidationException(
                    AcademicErrorCode.INVALID_CREDIT_LOAD,
                    "Institution minimum credits cannot exceed the maximum");
        }
        if (programmeMin != null) {
            requireRange("programme minimum", programmeMin);
        }
        if (programmeMax != null) {
            requireRange("programme maximum", programmeMax);
        }
        int min = programmeMin != null ? programmeMin : institutionMin;
        int max = programmeMax != null ? programmeMax : institutionMax;
        if (min > max) {
            throw new ValidationException(
                    AcademicErrorCode.INVALID_CREDIT_LOAD,
                    "Semester credit minimum (" + min + ") cannot exceed the maximum (" + max + ")");
        }
        return new CreditLoad(min, max, programmeMin != null || programmeMax != null);
    }

    private static void requireRange(String label, int value) {
        if (value < MIN_ALLOWED || value > MAX_ALLOWED) {
            throw new ValidationException(
                    AcademicErrorCode.INVALID_CREDIT_LOAD,
                    label + " must be between " + MIN_ALLOWED + " and " + MAX_ALLOWED);
        }
    }
}
