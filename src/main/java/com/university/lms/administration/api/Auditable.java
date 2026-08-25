package com.university.lms.administration.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful completion should write an {@link AuditTrail} event
 * automatically, instead of the method calling {@code auditTrail.record(...)} by hand.
 *
 * <p>{@code entityId} and {@code details} are Spring Expression Language, evaluated against the
 * method's parameters by name (e.g. {@code "#feeId"}) and, since the event is only written after
 * the method returns normally, the return value as {@code #result} (e.g.
 * {@code "#result.id()"} for a {@code create}-shaped method whose id only exists on the response).
 * {@code entityId} must evaluate to a {@code UUID} or {@code null}; {@code details}, if not blank,
 * must evaluate to a {@code String}.
 *
 * <p>The actor is resolved automatically from {@code CurrentUserProvider}, the same as every other
 * write in the system — there is nothing to name it in the annotation. {@code reason} and the
 * before/after snapshot are not exposed here: a SpEL expression capturing "the state before this
 * method mutated it" would have to run before the join point, defeating the point of
 * {@code @AfterReturning}, and every existing correction-shaped write (a grade change, a status
 * override) already calls {@link AuditTrail}'s full form directly for exactly that reason. This
 * annotation is for the simpler, far more common case — a routine creation or replacement with
 * nothing to explain — where a hand-written call would just repeat the same five lines at every
 * call site.
 *
 * <p>Enforced by {@code AuditableAspect}, an {@code @AfterReturning} advice: it only fires once the
 * method returns without throwing, so a rejected or failed write is correctly never recorded as
 * having happened. Like any Spring AOP advice, it only intercepts calls that arrive through the
 * proxy — a method on the same bean calling another {@code @Auditable} method directly, rather than
 * through an injected reference to itself, will not trigger it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** One of {@link AuditTrail.Action}'s constants. */
    String action();

    /** One of {@link AuditTrail.EntityType}'s constants. */
    String entityType();

    /** SpEL, evaluated against the method's parameters and {@code #result}. Must yield a UUID. */
    String entityId();

    /** SpEL yielding a String, or blank for no details. */
    String details() default "";
}
