package com.university.lms.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

public record ReplaceSectionMeetingsRequest(
        @NotNull @Valid List<MeetingRequest> meetings, Boolean allowConflicts) {

    /** See {@code AssignLecturerRequest#allowConflicts} — same reasoning, same audit. */
    public boolean overrideRequested() {
        return Boolean.TRUE.equals(allowConflicts);
    }


    public record MeetingRequest(
            @NotNull @Min(1) @Max(6) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotBlank @Size(max = 40) String room,
            @NotBlank @Size(max = 20) String sessionType) {}
}
