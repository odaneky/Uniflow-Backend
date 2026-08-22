package com.university.lms.academic.dto;

import com.university.lms.academic.domain.Faculty;
import java.util.UUID;

public record FacultyResponse(UUID id, String code, String name, UUID deanUserId) {

    public static FacultyResponse from(Faculty faculty) {
        return new FacultyResponse(faculty.getId(), faculty.getCode(), faculty.getName(), faculty.getDeanUserId());
    }
}
