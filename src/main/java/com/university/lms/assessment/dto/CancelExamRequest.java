package com.university.lms.assessment.dto;

import jakarta.validation.constraints.Size;

/**
 * Why a sitting was withdrawn.
 *
 * <p>Optional but strongly wanted: the reason is repeated to every student who could see the exam,
 * and "your exam has been cancelled" with no explanation is what fills the examinations office's
 * inbox.
 */
public record CancelExamRequest(@Size(max = 500) String reason) {}
