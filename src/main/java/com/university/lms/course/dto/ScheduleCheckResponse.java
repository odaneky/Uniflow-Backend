package com.university.lms.course.dto;

import java.util.List;

/**
 * What would stop this being saved.
 *
 * <p>Advisory only — nothing is written and nothing is reserved. A clash found here can still appear
 * between the check and the save if somebody else books the room in between, which is why the write
 * path checks again rather than trusting this.
 */
public record ScheduleCheckResponse(boolean clear, List<Conflict> conflicts) {

    public enum Kind {
        LECTURER,
        ROOM
    }

    public record Conflict(Kind kind, String message) {}

    public static ScheduleCheckResponse of(List<Conflict> conflicts) {
        return new ScheduleCheckResponse(conflicts.isEmpty(), conflicts);
    }
}
