package com.university.lms.admissions.dto;

import jakarta.validation.constraints.Size;

public record TransitionApplicationRequest(@Size(max = 2000) String note) {}
