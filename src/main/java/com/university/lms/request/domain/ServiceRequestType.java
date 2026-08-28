package com.university.lms.request.domain;

/** The kinds of registry request a student can file. */
public enum ServiceRequestType {
    TRANSCRIPT,
    WITHDRAWAL,
    VERIFICATION,
    APPEAL,
    GRADUATION,
    PROFILE_CORRECTION,
    SAP_APPEAL,
    LATE_ADD,
    COURSE_SUBSTITUTION,
    LEAVE_OF_ABSENCE,
    READMISSION,
    PROGRAMME_TRANSFER;

    public String referencePrefix() {
        return switch (this) {
            case TRANSCRIPT -> "TR";
            case WITHDRAWAL -> "WD";
            case VERIFICATION -> "EV";
            case APPEAL -> "GA";
            case GRADUATION -> "GR";
            case PROFILE_CORRECTION -> "PC";
            case SAP_APPEAL -> "SA";
            case LATE_ADD -> "LA";
            case COURSE_SUBSTITUTION -> "CS";
            case LEAVE_OF_ABSENCE -> "LO";
            case READMISSION -> "RA";
            case PROGRAMME_TRANSFER -> "PT";
        };
    }

    public String displayName() {
        return switch (this) {
            case TRANSCRIPT -> "Transcript";
            case WITHDRAWAL -> "Course Withdrawal";
            case VERIFICATION -> "Enrollment Verification";
            case APPEAL -> "Grade Appeal";
            case GRADUATION -> "Graduation Application";
            case PROFILE_CORRECTION -> "Profile Correction";
            case SAP_APPEAL -> "SAP Appeal";
            case LATE_ADD -> "Late Add Petition";
            case COURSE_SUBSTITUTION -> "Course Substitution";
            case LEAVE_OF_ABSENCE -> "Leave of Absence";
            case READMISSION -> "Readmission";
            case PROGRAMME_TRANSFER -> "Programme Transfer";
        };
    }

    public String reviewStep() {
        return switch (this) {
            case TRANSCRIPT, VERIFICATION, PROFILE_CORRECTION, READMISSION -> "Registrar Review";
            case WITHDRAWAL -> "Advisor Review";
            case APPEAL -> "Department Review";
            case GRADUATION -> "Degree Audit";
            case SAP_APPEAL -> "Financial Aid Review";
            case LATE_ADD -> "Registrar Review";
            case COURSE_SUBSTITUTION -> "Degree Audit";
            case LEAVE_OF_ABSENCE -> "Registrar Review";
            case PROGRAMME_TRANSFER -> "Registrar Review";
        };
    }

    /**
     * D9: the service-level window a submission of this type is expected to be decided within,
     * used to compute {@code ServiceRequest.dueAt} at creation. A rough operational target, not a
     * contractual SLA — nothing currently escalates automatically when it passes; a request past
     * its {@code dueAt} is simply reported as overdue for staff to triage.
     */
    public int slaDays() {
        return switch (this) {
            case VERIFICATION, LATE_ADD -> 3;
            case TRANSCRIPT, PROFILE_CORRECTION -> 5;
            case WITHDRAWAL -> 7;
            case COURSE_SUBSTITUTION, LEAVE_OF_ABSENCE -> 10;
            case APPEAL, SAP_APPEAL, READMISSION, PROGRAMME_TRANSFER -> 14;
            case GRADUATION -> 21;
        };
    }
}
