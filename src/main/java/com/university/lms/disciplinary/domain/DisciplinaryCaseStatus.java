package com.university.lms.disciplinary.domain;

import java.util.Map;
import java.util.Set;

/** OPEN until an officer is assigned, then UNDER_REVIEW until resolved one of two ways. */
public enum DisciplinaryCaseStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED;

    private static final Map<DisciplinaryCaseStatus, Set<DisciplinaryCaseStatus>> ALLOWED_TRANSITIONS = Map.of(
            OPEN, Set.of(UNDER_REVIEW, RESOLVED, DISMISSED),
            UNDER_REVIEW, Set.of(RESOLVED, DISMISSED),
            RESOLVED, Set.of(),
            DISMISSED, Set.of());

    public boolean canTransitionTo(DisciplinaryCaseStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isClosed() {
        return this == RESOLVED || this == DISMISSED;
    }
}
