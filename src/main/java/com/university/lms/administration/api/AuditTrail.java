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
        /** A candidate was reported for a conduct breach during a sitting. */
        public static final String EXAM_MISCONDUCT_REPORTED = "EXAM_MISCONDUCT_REPORTED";
        /** G6: a staff member was assigned to invigilate a sitting. */
        public static final String EXAM_INVIGILATOR_ASSIGNED = "EXAM_INVIGILATOR_ASSIGNED";
        /** G6: a staff member was withdrawn from invigilating a sitting. */
        public static final String EXAM_INVIGILATOR_UNASSIGNED = "EXAM_INVIGILATOR_UNASSIGNED";
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
        public static final String ADVISING_APPOINTMENT_SCHEDULED = "ADVISING_APPOINTMENT_SCHEDULED";
        public static final String ADVISING_APPOINTMENT_CANCELLED = "ADVISING_APPOINTMENT_CANCELLED";
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
        /** G5: an admissions officer confirmed an attached document is genuine. */
        public static final String APPLICATION_DOCUMENT_VERIFIED = "APPLICATION_DOCUMENT_VERIFIED";
        /** G5: an admissions officer rejected an attached document — the applicant must resubmit it. */
        public static final String APPLICATION_DOCUMENT_REJECTED = "APPLICATION_DOCUMENT_REJECTED";
        /** @deprecated superseded by {@link #LEDGER_ENTRY_PROPOSED} — E3 gave manual entries a review step. */
        @Deprecated
        public static final String LEDGER_ENTRY_POSTED = "LEDGER_ENTRY_POSTED";
        /** A staff member proposed a manual charge or credit; it does not affect the balance yet. */
        public static final String LEDGER_ENTRY_PROPOSED = "LEDGER_ENTRY_PROPOSED";
        /** A different staff member approved a proposed ledger entry, posting it to the balance. */
        public static final String LEDGER_ENTRY_APPROVED = "LEDGER_ENTRY_APPROVED";
        /** A different staff member rejected a proposed ledger entry; it never posts. */
        public static final String LEDGER_ENTRY_REJECTED = "LEDGER_ENTRY_REJECTED";
        public static final String FEE_CREATED = "FEE_CREATED";
        public static final String FEE_UPDATED = "FEE_UPDATED";
        public static final String FEE_DEACTIVATED = "FEE_DEACTIVATED";
        public static final String PAYMENT_PLAN_REPLACED = "PAYMENT_PLAN_REPLACED";
        public static final String TUITION_SCHEDULE_REPLACED = "TUITION_SCHEDULE_REPLACED";
        public static final String REFUND_POLICY_REPLACED = "REFUND_POLICY_REPLACED";
        public static final String AWARD_ACCEPTED = "AWARD_ACCEPTED";
        /** A Title IV or institutional award was marked as paid out. */
        public static final String AWARD_DISBURSED = "AWARD_DISBURSED";
        public static final String SERVICE_HOLD_PLACED = "SERVICE_HOLD_PLACED";
        public static final String SERVICE_HOLD_CLEARED = "SERVICE_HOLD_CLEARED";
        public static final String REQUIREMENT_BLOCK_CREATED = "REQUIREMENT_BLOCK_CREATED";
        public static final String REQUIREMENT_BLOCK_COURSE_ADDED = "REQUIREMENT_BLOCK_COURSE_ADDED";
        public static final String REQUIREMENT_BLOCK_DELETED = "REQUIREMENT_BLOCK_DELETED";
        /** A programme's draft curriculum version was published; its requirement blocks are now immutable. */
        public static final String CURRICULUM_VERSION_PUBLISHED = "CURRICULUM_VERSION_PUBLISHED";
        /** A student's open primary programme enrolment was bound to the curriculum version governing it, once. */
        public static final String CURRICULUM_VERSION_BOUND = "CURRICULUM_VERSION_BOUND";
        public static final String DEGREE_CONFERRED = "DEGREE_CONFERRED";
        public static final String COURSE_SUBSTITUTION_RECORDED = "COURSE_SUBSTITUTION_RECORDED";
        public static final String ACADEMIC_POLICY_REPLACED = "ACADEMIC_POLICY_REPLACED";
        public static final String PROGRAMME_CREDIT_LOAD_REPLACED = "PROGRAMME_CREDIT_LOAD_REPLACED";
        public static final String FACULTY_CREATED = "FACULTY_CREATED";
        public static final String DEPARTMENT_CREATED = "DEPARTMENT_CREATED";
        public static final String BUILDING_CREATED = "BUILDING_CREATED";
        public static final String ROOM_CREATED = "ROOM_CREATED";
        public static final String PROGRAMME_CREATED = "PROGRAMME_CREATED";
        public static final String PROGRAMME_UPDATED = "PROGRAMME_UPDATED";
        public static final String ACADEMIC_YEAR_CREATED = "ACADEMIC_YEAR_CREATED";
        public static final String ACADEMIC_TERM_CREATED = "ACADEMIC_TERM_CREATED";
        /** Opens or moves a term's registration window — the switch that lets students compete for seats. */
        public static final String REGISTRATION_WINDOW_SET = "REGISTRATION_WINDOW_SET";
        public static final String ADD_DROP_WINDOW_SET = "ADD_DROP_WINDOW_SET";
        /** The term-level examination period, distinct from EXAM_RESCHEDULED's individual sitting. */
        public static final String EXAM_WINDOW_SET = "EXAM_WINDOW_SET";
        public static final String DISCIPLINARY_CASE_FILED = "DISCIPLINARY_CASE_FILED";
        public static final String DISCIPLINARY_CASE_OFFICER_ASSIGNED = "DISCIPLINARY_CASE_OFFICER_ASSIGNED";
        public static final String DISCIPLINARY_CASE_NOTE_ADDED = "DISCIPLINARY_CASE_NOTE_ADDED";
        public static final String DISCIPLINARY_CASE_CLOSED = "DISCIPLINARY_CASE_CLOSED";

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
        public static final String FEE = "Fee";
        public static final String PAYMENT_PLAN = "PaymentPlan";
        public static final String TUITION_SCHEDULE = "TuitionSchedule";
        public static final String REFUND_POLICY = "RefundPolicy";
        public static final String FINANCIAL_AID_AWARD = "FinancialAidAward";
        public static final String SERVICE_HOLD = "ServiceHold";
        public static final String REQUIREMENT_BLOCK = "RequirementBlock";
        public static final String PROGRAMME = "Programme";
        public static final String FACULTY = "Faculty";
        public static final String DEPARTMENT = "Department";
        public static final String BUILDING = "Building";
        public static final String ROOM = "Room";
        public static final String ACADEMIC_YEAR = "AcademicYear";
        public static final String ACADEMIC_TERM = "AcademicTerm";
        /** No natural id — institution-wide singleton configuration (policy, tuition schedule). */
        public static final String INSTITUTION = "Institution";
        public static final String DISCIPLINARY_CASE = "DisciplinaryCase";

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
