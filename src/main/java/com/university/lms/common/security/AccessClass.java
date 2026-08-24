package com.university.lms.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares, on every controller endpoint method, who may reach it — coarsely, the same way
 * {@code SecurityConfig}'s role rules are coarse: this says who may call the URL at all, not
 * whether the specific record behind an id belongs to them. That finer check still lives in the
 * service layer, in {@code CurrentUser.requireSelfOrStaff} and its relatives.
 *
 * <p>The point is not precision. It is that {@code GET /api/v1/** → authenticated()} in
 * {@code SecurityConfig} is a fail-<em>open</em> default: forget a narrower rule for a new endpoint
 * and every signed-in caller — student included — can reach it, silently, with nothing to catch the
 * omission. {@code com.university.lms.architecture.AccessClassCoverageTest} makes the omission
 * itself impossible: every controller method must carry one of these values or the build fails.
 * That test is what actually closes the gap; this annotation is just what it reads.
 *
 * @see Value
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessClass {

    Value value();

    /** Coarse caller categories. Each name states who reaches the URL; it does not describe every
     * refinement the service layer applies underneath — see the per-value note for what to expect. */
    enum Value {
        /** No authentication required — the request never carries a bearer token. Used sparingly,
         *  and each one should have a specific reason: an applicant with no account yet, a public
         *  catalog page, a health check. */
        PUBLIC,

        /** Any signed-in member of the university, and deliberately nothing narrower — no role
         *  check, no ownership check underneath. This is the correct label for institution-wide
         *  reference data everyone is allowed to see once authenticated: the course catalog, the
         *  academic calendar, the department and faculty structure. {@code docs/architecture.md}
         *  documents this as an intentional choice, not the gap the coverage test exists to catch —
         *  the gap is a method with <em>no</em> label at all, not one correctly labelled here. */
        AUTHENTICATED,

        /** Any authenticated caller may call the URL; the service layer then narrows to the
         *  caller's own record, or lets staff through unrestricted, via
         *  {@code CurrentUser.requireSelfOrStaff} or an equivalent same-shaped check. If a method
         *  carries this value with no such check underneath, that is the bug the coverage test
         *  cannot see and a reviewer must. Distinct from {@link #OWN_RECORD_ONLY}: this value's
         *  URL carries an id, and staff calling it may name someone else's. */
        SELF_OR_STAFF,

        /** The {@code /api/v1/me/**} shape: no id in the path at all, the caller's own identity
         *  resolved from the token, and nothing for even a staff caller to override — a lecturer
         *  hitting {@code GET /me/assessments} gets their own (empty) record, not license to pass
         *  someone else's id, because there is no id parameter to pass. If staff need another
         *  person's record, that is a different, separately-labelled endpoint, not this one with a
         *  parameter added. The large majority of {@code My*Controller} methods are this. */
        OWN_RECORD_ONLY,

        /** Restricted to one or more non-student roles by {@code SecurityConfig}'s role rule for
         *  this path. Often further scoped in the service layer (a lecturer to sections they teach,
         *  an advisor to their own advisees) — this value says "not a student, not the public",
         *  nothing about which staff role or what additional scoping applies. */
        STAFF_ONLY,

        /** Restricted to registry-level roles only — {@code SYSTEM_ADMIN}/{@code REGISTRAR}, or a
         *  comparably narrow set for the resource (see {@code SecurityConfig} for the exact rule on
         *  this path). Used for actions no ordinary staff role should reach: opening a registration
         *  window, posting a ledger entry, reading the audit trail. */
        REGISTRY_ONLY
    }
}
