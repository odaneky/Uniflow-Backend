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
        public static final String IDENTITY_LINKED = "IDENTITY_LINKED";
        public static final String IDENTITY_LINK_REFUSED = "IDENTITY_LINK_REFUSED";
        public static final String IDENTITY_SYNC_FAILED = "IDENTITY_SYNC_FAILED";
        public static final String ACCOUNT_ENABLED = "ACCOUNT_ENABLED";
        public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
        public static final String ROLE_GRANTED = "ROLE_GRANTED";
        public static final String ROLE_REVOKED = "ROLE_REVOKED";
        public static final String STUDENT_PROVISIONED = "STUDENT_PROVISIONED";
        public static final String ENROLMENT_CREATED = "ENROLMENT_CREATED";
        public static final String ENROLMENT_DROPPED = "ENROLMENT_DROPPED";
        public static final String ENROLMENT_WITHDRAWN = "ENROLMENT_WITHDRAWN";
        public static final String OCCURRENCE_CREATED = "OCCURRENCE_CREATED";
        public static final String OCCURRENCE_UPDATED = "OCCURRENCE_UPDATED";
        public static final String OCCURRENCE_CANCELLED = "OCCURRENCE_CANCELLED";
        public static final String GRADE_PUBLISHED = "GRADE_PUBLISHED";
        public static final String GRADE_CHANGED = "GRADE_CHANGED";
        public static final String MESSAGE_THREAD_ACCESSED = "MESSAGE_THREAD_ACCESSED";

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
    void record(
            UUID actorUserId, String actorLabel, String action, String entityType, UUID entityId, String details);
}
