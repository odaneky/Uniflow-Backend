package com.university.lms.request.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.request.domain.ServiceRequest;
import java.util.UUID;

/** Payload field helpers — keeps workflow free of raw JSON parsing. */
public final class ServiceRequestPayloads {

    private ServiceRequestPayloads() {}

    public static UUID enrollmentId(String payloadJson) {
        return uuidField(payloadJson, "enrollmentId");
    }

    public static UUID gradeId(String payloadJson) {
        return uuidField(payloadJson, "gradeId");
    }

    public static UUID expectedGraduationTermId(String payloadJson) {
        return uuidField(payloadJson, "expectedGraduationTermId");
    }

    public static UUID courseSectionId(String payloadJson) {
        return uuidField(payloadJson, "courseSectionId");
    }

    public static UUID requiredCourseId(String payloadJson) {
        return uuidField(payloadJson, "requiredCourseId");
    }

    public static UUID substituteCourseId(String payloadJson) {
        return uuidField(payloadJson, "substituteCourseId");
    }

    private static UUID uuidField(String payloadJson, String field) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(payloadJson);
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            return UUID.fromString(value.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    public static UUID gradeIdFrom(ServiceRequest request) {
        return gradeId(request.getPayload());
    }
}
