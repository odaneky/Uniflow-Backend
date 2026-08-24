package com.university.lms.grading.dto;

import java.util.UUID;

public record TermCloseResponse(
        UUID academicTermId,
        int gradesConsidered,
        int gradesNewlyLocked,
        int studentsConsidered,
        int recordsWritten,
        int studentsAlreadyClosed) {}
