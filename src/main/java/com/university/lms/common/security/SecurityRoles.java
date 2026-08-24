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

    public static final String ROLE_PREFIX = "ROLE_";

    /** Every role except {@code STUDENT} — the set {@code CurrentUser.isStaff()} currently defines by exclusion. */
    public static final java.util.Set<String> STAFF_ROLES =
            java.util.Set.of(LECTURER, ACADEMIC_ADVISOR, FACULTY_ADMIN, REGISTRAR, SYSTEM_ADMIN);

    private SecurityRoles() {}

    public static String authority(String role) {
        return ROLE_PREFIX + role;
    }
}
