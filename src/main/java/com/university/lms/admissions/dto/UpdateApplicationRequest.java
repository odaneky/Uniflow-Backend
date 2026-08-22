package com.university.lms.admissions.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateApplicationRequest(
        @Email(message = "must be a valid email address")
                @Size(max = 255, message = "must be at most 255 characters")
                String applicantEmail,
        @Size(max = 200, message = "must be at most 200 characters") String applicantName,
        Map<String, Object> payload) {}
