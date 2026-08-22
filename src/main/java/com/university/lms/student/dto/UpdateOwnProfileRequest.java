package com.university.lms.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Contact details the student (or registrar) may maintain. Never includes student number. */
public record UpdateOwnProfileRequest(
        @Email(message = "must be a valid email address") @Size(max = 254, message = "must be at most 254 characters")
                String personalEmail,
        @Size(max = 30, message = "must be at most 30 characters") String gender,
        @Size(max = 30, message = "must be at most 30 characters") String phoneNumber,
        LocalDate dateOfBirth,
        @Size(max = 100, message = "must be at most 100 characters") String nationality,
        @Size(max = 255, message = "must be at most 255 characters") String addressLine1,
        @Size(max = 255, message = "must be at most 255 characters") String addressLine2,
        @Size(max = 100, message = "must be at most 100 characters") String city,
        @Size(max = 100, message = "must be at most 100 characters") String country,
        @Size(max = 200, message = "must be at most 200 characters") String emergencyContactName,
        @Size(max = 30, message = "must be at most 30 characters") String emergencyContactPhone) {}
