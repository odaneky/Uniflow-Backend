package com.university.lms.student.api;

import java.util.UUID;

/**
 * The one-directional write path {@code curriculum} uses to bind a student's open primary
 * programme enrolment to the curriculum version that governs it.
 *
 * <p>{@code student} cannot depend on {@code curriculum} — that module already depends on {@code
 * student} (for {@link StudentDirectory}), and a dependency back would be the module graph's first
 * cycle — so {@code curriculum_version_id} cannot be resolved from inside {@code student} at the
 * moment a membership is opened. This interface is the other half of that design: {@code
 * curriculum} resolves the version it wants and calls back through here, never the reverse.
 */
public interface StudentProgrammeEnrolments {

    /**
     * Binds the student's open primary enrolment to a curriculum version, once.
     *
     * <p>A no-op when the enrolment is already bound to a version — binding never replaces an
     * already-resolved version, which is the entire point: a student's degree-audit answer must not
     * move when the programme's requirements are later revised. Also a no-op when the student has no
     * open primary enrolment.
     */
    void bindCurriculumVersion(UUID studentId, UUID curriculumVersionId);
}
