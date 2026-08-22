package com.university.lms.course.dto;

import java.time.LocalTime;
import java.util.UUID;

public record SectionMeetingResponse(
        UUID id, int dayOfWeek, String day, LocalTime startTime, LocalTime endTime, String room, String sessionType) {

    private static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri"};

    public static String dayName(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 5) {
            return "Day " + dayOfWeek;
        }
        return DAYS[dayOfWeek - 1];
    }
}
