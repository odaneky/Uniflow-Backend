package com.university.lms.student.domain;

/**
 * Academic standing, distinct from the login account's {@code UserStatus}: a graduated student
 * keeps an active account for transcript access long after they stop being enrolled.
 */
public enum StudentStatus {
    APPLICANT,
    ACTIVE,
    /** Approved temporary absence; the record is preserved and may return to {@link #ACTIVE}. */
    ON_LEAVE,
    SUSPENDED,
    WITHDRAWN,
    GRADUATED
}
