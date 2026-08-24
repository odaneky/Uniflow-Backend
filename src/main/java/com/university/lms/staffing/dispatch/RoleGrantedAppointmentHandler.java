package com.university.lms.staffing.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.identity.service.IdentityOutboxPublisher;
import com.university.lms.staffing.service.StaffingService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A5 groundwork: gives every newly staffed user a default institution-wide appointment, so
 * org-scoped authorization has real data to consult before any guard is narrowed to require it.
 * Narrows nothing on its own — {@code StaffingService.ensureAppointment} appoints at the
 * institution root, which {@code isAppointedOver} treats as covering every unit beneath it, so this
 * preserves today's "any staff role reaches everywhere" behaviour exactly.
 */
@Component
public class RoleGrantedAppointmentHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final StaffingService staffingService;

    public RoleGrantedAppointmentHandler(ObjectMapper objectMapper, StaffingService staffingService) {
        this.objectMapper = objectMapper;
        this.staffingService = staffingService;
    }

    @Override
    public String eventType() {
        return IdentityOutboxPublisher.EVENT_ROLE_GRANTED;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID userId = UUID.fromString(payload.get("userId").asText());
        String role = payload.get("role").asText();
        staffingService.ensureAppointment(userId, role);
    }
}
