package com.university.lms.request.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.enrollment.api.EnrollmentActions;
import com.university.lms.grading.api.GradeDirectory;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequestType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Validates type-specific JSON payloads on create. */
@Component
public class ServiceRequestPayloadValidator {

    private final ObjectMapper objectMapper;
    private final EnrollmentActions enrollmentActions;
    private final GradeDirectory gradeDirectory;
    private final AcademicStructure academicStructure;

    public ServiceRequestPayloadValidator(
            ObjectMapper objectMapper,
            EnrollmentActions enrollmentActions,
            GradeDirectory gradeDirectory,
            AcademicStructure academicStructure) {
        this.objectMapper = objectMapper;
        this.enrollmentActions = enrollmentActions;
        this.gradeDirectory = gradeDirectory;
        this.academicStructure = academicStructure;
    }

    public String validateAndNormalize(ServiceRequestType type, Object payload, UUID studentId) {
        JsonNode node = payload == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(payload);
        return switch (type) {
            case WITHDRAWAL -> validateWithdrawal(node, studentId);
            case APPEAL -> validateAppeal(node, studentId);
            case TRANSCRIPT -> validateTranscript(node);
            case VERIFICATION -> validateVerification(node);
            case GRADUATION -> validateGraduation(node);
            case PROFILE_CORRECTION -> validateProfileCorrection(node);
            case SAP_APPEAL -> validateSapAppeal(node);
            case LATE_ADD -> validateLateAdd(node);
            case COURSE_SUBSTITUTION -> validateCourseSubstitution(node);
            case LEAVE_OF_ABSENCE -> validateLeaveOfAbsence(node);
            case READMISSION -> validateReadmission(node);
            case PROGRAMME_TRANSFER -> validateProgrammeTransfer(node);
        };
    }

    private String validateWithdrawal(JsonNode node, UUID studentId) {
        UUID enrollmentId = requireUuid(node, "enrollmentId");
        if (!enrollmentActions.canWithdraw(enrollmentId, studentId)) {
            throw new ValidationException(
                    RequestErrorCode.REQUEST_INVALID_PAYLOAD,
                    "That enrolment cannot be withdrawn or does not belong to you");
        }
        return node.toString();
    }

    private String validateAppeal(JsonNode node, UUID studentId) {
        UUID gradeId = requireUuid(node, "gradeId");
        GradeDirectory.GradeSummary grade = gradeDirectory
                .findById(gradeId)
                .orElseThrow(() -> new ValidationException(
                        RequestErrorCode.REQUEST_INVALID_PAYLOAD, "No grade exists with that id"));
        if (!grade.studentId().equals(studentId)) {
            throw new ValidationException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "That grade is not yours");
        }
        if (!grade.published()) {
            throw new ValidationException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "Only published grades may be appealed");
        }
        if (grade.underAppeal()) {
            throw new ValidationException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "That grade is already under appeal");
        }
        requireText(node, "reason");
        return node.toString();
    }

    private String validateTranscript(JsonNode node) {
        requireText(node, "deliveryMethod");
        return node.toString();
    }

    private String validateVerification(JsonNode node) {
        requireText(node, "purpose");
        return node.toString();
    }

    private String validateGraduation(JsonNode node) {
        if (node.hasNonNull("expectedGraduationTermId")) {
            requireUuid(node, "expectedGraduationTermId");
        }
        return node.toString();
    }

    private String validateProfileCorrection(JsonNode node) {
        requireText(node, "reason");
        boolean hasCorrection = node.hasNonNull("personalEmail")
                || node.hasNonNull("gender")
                || node.hasNonNull("phoneNumber")
                || node.hasNonNull("dateOfBirth")
                || node.hasNonNull("nationality")
                || node.hasNonNull("addressLine1")
                || node.hasNonNull("addressLine2")
                || node.hasNonNull("city")
                || node.hasNonNull("country")
                || node.hasNonNull("emergencyContactName")
                || node.hasNonNull("emergencyContactPhone");
        if (!hasCorrection) {
            throw new ValidationException(
                    RequestErrorCode.REQUEST_INVALID_PAYLOAD,
                    "At least one corrected field is required");
        }
        return node.toString();
    }

    private String validateSapAppeal(JsonNode node) {
        requireText(node, "reason");
        if (node.hasNonNull("termId")) {
            requireUuid(node, "termId");
        }
        return node.toString();
    }

    private String validateLateAdd(JsonNode node) {
        requireUuid(node, "courseSectionId");
        requireText(node, "reason");
        return node.toString();
    }

    private String validateCourseSubstitution(JsonNode node) {
        requireUuid(node, "requiredCourseId");
        requireUuid(node, "substituteCourseId");
        requireText(node, "reason");
        return node.toString();
    }

    private String validateLeaveOfAbsence(JsonNode node) {
        requireText(node, "reason");
        if (node.hasNonNull("expectedReturnTermId")) {
            requireUuid(node, "expectedReturnTermId");
        }
        return node.toString();
    }

    private String validateReadmission(JsonNode node) {
        requireText(node, "reason");
        return node.toString();
    }

    private String validateProgrammeTransfer(JsonNode node) {
        UUID newProgrammeId = requireUuid(node, "newProgrammeId");
        if (!academicStructure.programmeExists(newProgrammeId)) {
            throw new ValidationException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "No programme exists with that id");
        }
        requireText(node, "reason");
        return node.toString();
    }

    private static UUID requireUuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            throw new ValidationException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, field + " is required");
        }
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, field + " must be a valid id");
        }
    }

    private static void requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new ValidationException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, field + " is required");
        }
    }
}
