package com.university.lms.request.domain;

/** The kinds of registry request a student can file. */
public enum ServiceRequestType {
    TRANSCRIPT,
    WITHDRAWAL,
    VERIFICATION,
    APPEAL,
    GRADUATION;

    public String referencePrefix() {
        return switch (this) {
            case TRANSCRIPT -> "TR";
            case WITHDRAWAL -> "WD";
            case VERIFICATION -> "EV";
            case APPEAL -> "GA";
            case GRADUATION -> "GR";
        };
    }

    public String displayName() {
        return switch (this) {
            case TRANSCRIPT -> "Transcript";
            case WITHDRAWAL -> "Course Withdrawal";
            case VERIFICATION -> "Enrollment Verification";
            case APPEAL -> "Grade Appeal";
            case GRADUATION -> "Graduation Application";
        };
    }

    public String reviewStep() {
        return switch (this) {
            case TRANSCRIPT, VERIFICATION -> "Registrar Review";
            case WITHDRAWAL -> "Advisor Review";
            case APPEAL -> "Department Review";
            case GRADUATION -> "Degree Audit";
        };
    }
}
