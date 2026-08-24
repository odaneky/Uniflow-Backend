package com.university.lms.course.api;

import java.util.UUID;

/**
 * Section-level writes triggered from outside the course module — separate from {@link
 * CourseCatalog} the way {@code financialaid.api.HoldActions} is separate from a read-shaped
 * contract.
 *
 * <p>{@code enrollment} already depends on {@code course} for {@link CourseCatalog}; a full section
 * cancellation (releasing every affected student's seat, reversing their charges, notifying them)
 * has to happen from that side, since {@code course} cannot depend back on {@code enrollment}
 * without the module graph's first cycle. This is the one write {@code course} needs to expose so
 * that orchestration can live there — see {@code enrollment.service.SectionCancellationService}.
 */
public interface SectionActions {

    /** Cancels the section itself. Does not touch enrolments — the caller is responsible for those. */
    void cancel(UUID sectionId);
}
