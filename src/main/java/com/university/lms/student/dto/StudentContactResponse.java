package com.university.lms.student.dto;

import java.time.LocalDate;

public record StudentContactResponse(
        String phoneNumber,
        LocalDate dateOfBirth,
        String nationality,
        String addressLine1,
        String addressLine2,
        String city,
        String country,
        String emergencyContactName,
        String emergencyContactPhone) {}
