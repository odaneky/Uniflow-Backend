package com.university.lms.identity.domain;

/** Lifecycle of a login account, independent of any academic standing. */
public enum UserStatus {

    /** Created but not yet able to authenticate. */
    PENDING_ACTIVATION,

    ACTIVE,

    /** Temporarily blocked, e.g. by an administrator or after repeated failed logins. */
    SUSPENDED,

    /** Permanently retired. Rows are retained for audit and academic-record integrity. */
    DEACTIVATED
}
