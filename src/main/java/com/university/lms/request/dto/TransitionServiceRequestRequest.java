package com.university.lms.request.dto;

import jakarta.validation.constraints.Size;

public record TransitionServiceRequestRequest(@Size(max = 2000) String note) {}
