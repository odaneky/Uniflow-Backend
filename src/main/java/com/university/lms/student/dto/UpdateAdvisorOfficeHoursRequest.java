package com.university.lms.student.dto;

import jakarta.validation.constraints.Size;

/** Advisor-owned office hours shown on advisee records. */
public record UpdateAdvisorOfficeHoursRequest(@Size(max = 200) String officeHours) {}
