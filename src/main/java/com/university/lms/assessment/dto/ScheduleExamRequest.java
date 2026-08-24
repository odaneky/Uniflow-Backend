package com.university.lms.assessment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Schedules a sitting. Not published by the act of scheduling — a draft timetable is worked on for
 * weeks and is wrong for most of that time.
 *
 * @param seating free text, because universities describe seating in prose: "Rows 1–12",
 *     "Alphabetical A–K". A numeric range would be a model nobody could use.
 */
public record ScheduleExamRequest(
        @NotBlank(message = "is required") @Size(max = 100) String title,
        @NotNull(message = "is required") Instant startsAt,
        @NotNull(message = "is required") @Min(1) @Max(600) Integer durationMinutes,
        @NotBlank(message = "is required") @Size(max = 60) String room,
        @Size(max = 120) String seating,
        UUID assessmentId) {}
