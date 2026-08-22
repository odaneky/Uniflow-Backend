package com.university.lms.request.service.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.student.api.StudentLifecycle;
import com.university.lms.student.dto.UpdateOwnProfileRequest;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ProfileCorrectionFulfillmentApplier implements RequestFulfillmentApplier {

    private final StudentLifecycle studentLifecycle;
    private final ObjectMapper objectMapper;

    public ProfileCorrectionFulfillmentApplier(StudentLifecycle studentLifecycle, ObjectMapper objectMapper) {
        this.studentLifecycle = studentLifecycle;
        this.objectMapper = objectMapper;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.PROFILE_CORRECTION;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        JsonNode node = parsePayload(request.getPayload());
        studentLifecycle.applyContactCorrection(
                request.getStudentId(),
                new UpdateOwnProfileRequest(
                        textOrNull(node, "personalEmail"),
                        textOrNull(node, "gender"),
                        textOrNull(node, "phoneNumber"),
                        dateOrNull(node, "dateOfBirth"),
                        textOrNull(node, "nationality"),
                        textOrNull(node, "addressLine1"),
                        textOrNull(node, "addressLine2"),
                        textOrNull(node, "city"),
                        textOrNull(node, "country"),
                        textOrNull(node, "emergencyContactName"),
                        textOrNull(node, "emergencyContactPhone")));
    }

    private JsonNode parsePayload(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid profile correction payload", ex);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static LocalDate dateOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) {
            return null;
        }
        return LocalDate.parse(text);
    }
}
