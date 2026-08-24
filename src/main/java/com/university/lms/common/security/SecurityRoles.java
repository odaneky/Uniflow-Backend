package com.university.lms.common.security;

/**
 * Canonical role names, shared so that authorization expressions and the seeded role rows cannot
 * drift apart through a typo in a string literal.
 *
 * <p>Authority strings carry the {@code ROLE_} prefix Spring Security expects; the bare names are
 * what the {@code roles} table stores.
 */
public final class SecurityRoles {

    public static final String STUDENT = "STUDENT";
    public static final String LECTURER = "LECTURER";
    public static final String ACADEMIC_ADVISOR = "ACADEMIC_ADVISOR";
    public static final String FACULTY_ADMIN = "FACULTY_ADMIN";
    public static final String REGISTRAR = "REGISTRAR";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    /**
     * A6 groundwork. {@code REGISTRAR} today spans records, the ledger, aid, admissions and exams —
     * these narrower roles are the eventual split. Additive only, so far: nothing has actually been
     * granted one yet in any real environment, and no guard requires one instead of {@code
     * REGISTRAR} — a guard narrowed to require one before that grant exists would lock out every
     * real registrar the day it shipped, unlike A5's org-scoping, which had {@code StaffAppointments}
     * to fail open against. Where a guard is widened at all in this pass, it is widened to accept
     * {@code REGISTRAR} <em>or</em> the narrower role, never narrowed to exclude {@code REGISTRAR}.
     */
    public static final String BURSAR = "BURSAR";

    public static final String FINANCIAL_AID_OFFICER = "FINANCIAL_AID_OFFICER";
    public static final String ADMISSIONS_OFFICER = "ADMISSIONS_OFFICER";
    public static final String EXAMS_OFFICER = "EXAMS_OFFICER";

    public static final String ROLE_PREFIX = "ROLE_";

    /** Every role except {@code STUDENT} — the set {@code CurrentUser.isStaff()} currently defines by exclusion. */
    public static final java.util.Set<String> STAFF_ROLES = java.util.Set.of(
            LECTURER,
            ACADEMIC_ADVISOR,
            FACULTY_ADMIN,
            REGISTRAR,
            SYSTEM_ADMIN,
            BURSAR,
            FINANCIAL_AID_OFFICER,
            ADMISSIONS_OFFICER,
            EXAMS_OFFICER);

    private SecurityRoles() {}

    public static String authority(String role) {
        return ROLE_PREFIX + role;
    }
}
