package com.university.lms.student.dto;

import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentProfile;
import com.university.lms.student.domain.StudentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Full representation of a student record, including contact details when the profile is loaded. */
public record StudentResponse(
        UUID id,
        UUID userId,
        String studentNumber,
        UUID programmeId,
        StudentStatus status,
        LocalDate admissionDate,
        LocalDate expectedGraduationDate,
        UUID advisorUserId,
        String advisorName,
        String advisorEmail,
        String advisorOfficeHours,
        ResidencyClassification residencyClassification,
        Instant createdAt,
        Instant updatedAt,
        StudentContactResponse contact) {

    public static StudentResponse from(Student student) {
        return from(student, null, null);
    }

    public static StudentResponse from(Student student, String advisorName, String advisorEmail) {
        return new StudentResponse(
                student.getId(),
                student.getUserId(),
                student.getStudentNumber(),
                student.getProgrammeId(),
                student.getStatus(),
                student.getAdmissionDate(),
                student.getExpectedGraduationDate(),
                student.getAdvisorUserId(),
                advisorName,
                advisorEmail,
                student.getAdvisorOfficeHours(),
                student.getResidencyClassification(),
                student.getCreatedAt(),
                student.getUpdatedAt(),
                fromProfile(student.getProfile()));
    }

    private static StudentContactResponse fromProfile(StudentProfile profile) {
        if (profile == null) {
            return null;
        }
        return new StudentContactResponse(
                profile.getPersonalEmail(),
                profile.getGender(),
                profile.getPhoneNumber(),
                profile.getDateOfBirth(),
                profile.getNationality(),
                profile.getAddressLine1(),
                profile.getAddressLine2(),
                profile.getCity(),
                profile.getCountry(),
                profile.getEmergencyContactName(),
                profile.getEmergencyContactPhone());
    }
}
