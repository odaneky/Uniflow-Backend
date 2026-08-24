package com.university.lms.staffing.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.service.AcademicOutboxPublisher;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.staffing.service.StaffingService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A5 groundwork: mirrors a newly created (or, via a reconcile pass, existing) faculty or
 * department as a real {@link com.university.lms.staffing.domain.OrgUnit}, so an appointment can
 * eventually be scoped to that specific unit instead of only the whole institution.
 */
@Component
public class AcademicOrgUnitHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final StaffingService staffingService;

    public AcademicOrgUnitHandler(ObjectMapper objectMapper, StaffingService staffingService) {
        this.objectMapper = objectMapper;
        this.staffingService = staffingService;
    }

    @Override
    public String eventType() {
        return AcademicOutboxPublisher.EVENT_ORG_UNIT_NEEDED;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        String sourceType = payload.get("sourceType").asText();
        UUID sourceId = UUID.fromString(payload.get("sourceId").asText());
        String code = payload.get("code").asText();
        String name = payload.get("name").asText();
        staffingService.ensureOrgUnitFor(sourceType, sourceId, code, name);
    }
}
