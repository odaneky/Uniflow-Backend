package com.university.lms.academic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.OutboxWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Enqueues academic-structure events other modules react to without depending on academic's internals. */
@Component
public class AcademicOutboxPublisher {

    public static final String EVENT_ORG_UNIT_NEEDED = "AcademicOrgUnitNeeded";

    private static final Logger log = LoggerFactory.getLogger(AcademicOutboxPublisher.class);

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public AcademicOutboxPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * A5 groundwork: {@code staffing.dispatch.AcademicOrgUnitHandler} reacts to this by mirroring
     * the faculty or department as a real {@code OrgUnit}, so a future staff appointment can be
     * scoped to it specifically instead of only the whole institution.
     *
     * @param sourceType {@code "FACULTY"} or {@code "DEPARTMENT"}
     */
    public void publishOrgUnitNeeded(String sourceType, UUID sourceId, String code, String name) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sourceType", sourceType);
            payload.put("sourceId", sourceId.toString());
            payload.put("code", code);
            payload.put("name", name);
            outboxWriter.enqueue(
                    sourceType,
                    sourceId,
                    EVENT_ORG_UNIT_NEEDED,
                    objectMapper.writeValueAsString(payload),
                    "OrgUnitNeeded:" + sourceType + ":" + sourceId + ":" + UUID.randomUUID());
        } catch (Exception ex) {
            log.warn("Could not enqueue org-unit-needed event for {} {}", sourceType, sourceId, ex);
        }
    }
}
