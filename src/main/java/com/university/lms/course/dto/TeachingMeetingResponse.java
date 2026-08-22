package com.university.lms.course.dto;

/** One scheduled meeting on a section the caller teaches. */
public record TeachingMeetingResponse(
        int dayOfWeek, String day, String startTime, String endTime, String room, String sessionType) {}
