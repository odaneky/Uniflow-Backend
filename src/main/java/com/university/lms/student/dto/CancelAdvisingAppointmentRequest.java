package com.university.lms.student.dto;

import jakarta.validation.constraints.Size;

public record CancelAdvisingAppointmentRequest(@Size(max = 500, message = "must be at most 500 characters") String reason) {}
