package com.university.lms.academic.dto;

import com.university.lms.academic.domain.Department;
import java.util.UUID;

public record DepartmentResponse(UUID id, UUID facultyId, String code, String name, UUID headUserId) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getFaculty().getId(),
                department.getCode(),
                department.getName(),
                department.getHeadUserId());
    }
}
