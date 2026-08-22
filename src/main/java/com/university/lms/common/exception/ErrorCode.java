package com.university.lms.common.exception;

/**
 * A stable, machine-readable error identifier returned to API clients.
 *
 * <p>Clients branch on {@link #code()} — never on the human-readable message, which is free to
 * change. Each module owns its own {@code ErrorCode} enum so that error vocabulary stays with the
 * module that defines it rather than accumulating in {@code common}.
 */
public interface ErrorCode {

    /** Stable SCREAMING_SNAKE_CASE identifier, e.g. {@code STUDENT_NOT_FOUND}. */
    String code();
}
