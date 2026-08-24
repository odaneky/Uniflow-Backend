package com.university.lms.administration.api;

import java.util.UUID;

/**
 * Records security- and identity-significant events.
 *
 * <p>Published as a contract so that any module can write to the trail without depending on the
 * administration module's tables. Deliberately narrow: this is an append-only record of things that
 * happened, not a general logging facility.
 *
 * <p><b>Never pass a token, a password, a bearer header or any other credential material in
 * {@code details}.</b> The trail is read by operators and exported to compliance tooling; anything
 * written here should be assumed to persist indefinitely.
 *
 * <p>The actor's display name is snapshotted at write time. Administration must not resolve names
 * from identity: identity already depends on this contract, and a lookup the other way would be a
 * cycle.
 */
public interface AuditTrail {

    /** Actions worth reconstructing after the fact. */
    final class Action {
        public static final String IDENTITY_PROVISIONED = "IDENTITY_PROVISIONED";
        /** An exam was moved, withdrawn or cancelled — students may have planned around it. */
        public static final String EXAM_RESCHEDULED = "EXAM_RESCHEDULED";
        public static final String EXAM_UNPUBLISHED = "EXAM_UNPUBLISHED";
        public static final String EXAM_CANCELLED = "EXAM_CANCELLED";
        /** Somebody knowingly scheduled a lecturer or a room into a clash. */
        public static final String SCHEDULE_CONFLICT_OVERRIDDEN = "SCHEDULE_CONFLICT_OVERRIDDEN";
        public static final String IDENTITY_LINKED = "IDENTITY_LINKED";
        public static final String IDENTITY_LINK_REFUSED = "IDENTITY_LINK_REFUSED";
        public static final String IDENTITY_SYNC_FAILED = "IDENTITY_SYNC_FAILED";
        public static final String ACCOUNT_ENABLED = "ACCOUNT_ENABLED";
        public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
        public static final String ROLE_GRANTED = "ROLE_GRANTED";
        public static final String ROLE_REVOKED = "ROLE_REVOKED";
        public static final String STUDENT_PROVISIONED = "STUDENT_PROVISIONED";
        public static final String STUDENT_STATUS_CHANGED = "STUDENT_STATUS_CHANGED";
        public static final String ENROLMENT_CREATED = "ENROLMENT_CREATED";
        public static final String ENROLMENT_DROPPED = "ENROLMENT_DROPPED";
        public static final String ENROLMENT_WITHDRAWN = "ENROLMENT_WITHDRAWN";
        /** A student's enrolment ended because their section was cancelled — not their choice. */
        public static final String ENROLMENT_CANCELLED_BY_INSTITUTION = "ENROLMENT_CANCELLED_BY_INSTITUTION";
        public static final String OCCURRENCE_CREATED = "OCCURRENCE_CREATED";
        public static final String OCCURRENCE_UPDATED = "OCCURRENCE_UPDATED";
        public static final String OCCURRENCE_CANCELLED = "OCCURRENCE_CANCELLED";
        public static final String GRADE_PUBLISHED = "GRADE_PUBLISHED";
        public static final String GRADE_CHANGED = "GRADE_CHANGED";
        public static final String MESSAGE_THREAD_ACCESSED = "MESSAGE_THREAD_ACCESSED";
        public static final String SERVICE_REQUEST_CREATED = "SERVICE_REQUEST_CREATED";
        public static final String SERVICE_REQUEST_TRANSITIONED = "SERVICE_REQUEST_TRANSITIONED";
        public static final String SERVICE_REQUEST_FULFILLED = "SERVICE_REQUEST_FULFILLED";
        public static final String APPLICATION_CREATED = "APPLICATION_CREATED";
        /** A resume link was reissued; the previous capability token stopped working. */
        public static final String APPLICATION_ACCESS_REISSUED = "APPLICATION_ACCESS_REISSUED";
        public static final String APPLICATION_TRANSITIONED = "APPLICATION_TRANSITIONED";
        public static final String APPLICATION_MATRICULATED = "APPLICATION_MATRICULATED";
        /** A registrar posted a manual, unreviewed charge or credit directly onto a student's ledger. */
        public static final String LEDGER_ENTRY_POSTED = "LEDGER_ENTRY_POSTED";

        private Action() {}
    }

    /** Stable type names stored on each row so the trail can be filtered without joining. */
    final class EntityType {
        public static final String USER = "User";
        public static final String STUDENT = "Student";
        public static final String ENROLLMENT = "Enrollment";
        public static final String COURSE_SECTION = "CourseSection";
        public static final String GRADE = "Grade";
        public static final String CONVERSATION = "Conversation";
        public static final String SERVICE_REQUEST = "ServiceRequest";
        public static final String APPLICATION = "Application";
        public static final String ACCOUNT_ENTRY = "AccountEntry";

        private EntityType() {}
    }

    /**
     * @param actorUserId the local user who caused this, or {@code null} when the actor is not yet
     *     resolvable — which is precisely the case during identity provisioning. Stored as a plain
     *     identifier with no foreign key: the trail must never take locks on, or be rewritten by,
     *     the tables it audits. See V27 for the deadlock that established this the hard way.
     * @param details short, human-readable context; must contain no credential material
     */
    default void record(UUID actorUserId, String action, String entityType, UUID entityId, String details) {
        record(actorUserId, null, action, entityType, entityId, details);
    }

    /**
     * @param actorLabel a display name snapshotted from the caller (username or full name). Never
     *     resolved later: the trail must remain readable after the actor's account is renamed or
     *     removed, and administration must not depend on identity to render a row.
     */
    default void record(
            UUID actorUserId, String actorLabel, String action, String entityType, UUID entityId, String details) {
        record(actorUserId, actorLabel, action, entityType, entityId, details, null, null, null);
    }

    /**
     * The full form. Prefer this over the shorter overloads for any write that corrects, reverses,
     * or overrides a prior value — a grade change, a manual ledger entry, a status override — since
     * {@code reason} and the before/after snapshot are exactly what turns "this changed" into
     * "this changed, to this, because of this, and here is what it looked like before".
     *
     * <p>{@code sourceIp} and {@code correlationId} are deliberately absent from this signature:
     * they are resolved by the implementation from the current request, the same way
     * {@code created_by} is resolved by {@code AuditorAwareImpl} rather than passed in. A caller
     * that could fabricate its own IP or correlation id would make the column worthless as evidence.
     *
     * @param reason why, in the actor's own words. Should be non-blank for a correction; may be
     *     {@code null} for a routine creation with nothing to explain.
     * @param beforeValue pre-serialized JSON snapshot of the affected fields before the change, or
     *     {@code null} when there was no prior state (a first creation).
     * @param afterValue pre-serialized JSON snapshot of the affected fields after the change.
     */
    void record(
            UUID actorUserId,
            String actorLabel,
            String action,
            String entityType,
            UUID entityId,
            String details,
            String reason,
            String beforeValue,
            String afterValue);
}
