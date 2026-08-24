package com.university.lms.course.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param allowConflicts deliberately override a detected clash. Timetabling has legitimate
 *     exceptions — cover arrangements, classes that alternate by week — and a hard block with no
 *     escape hatch gets worked around by entering wrong data, which is worse than the clash. Using
 *     it is recorded in the audit trail.
 */
public record AssignLecturerRequest(
        @NotNull(message = "is required") UUID lecturerUserId, Boolean allowConflicts) {

    public boolean overrideRequested() {
        return Boolean.TRUE.equals(allowConflicts);
    }
}
