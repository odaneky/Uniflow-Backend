package com.university.lms.request.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.university.lms.common.outbox.OutboxWriter;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ServiceRequestOutboxPublisher {

    public static final String AGGREGATE_TYPE = "ServiceRequest";
    public static final String EVENT_SUBMITTED = "ServiceRequestSubmitted";
    public static final String EVENT_STATUS_CHANGED = "ServiceRequestStatusChanged";
    public static final String EVENT_DELIVERED = "ServiceRequestDelivered";

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public ServiceRequestOutboxPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    public void publishSubmitted(ServiceRequest request) {
        ObjectNode payload = basePayload(request);
        enqueue(request, EVENT_SUBMITTED, payload, "ServiceRequestSubmitted:" + request.getId());
    }

    public void publishStatusChanged(
            ServiceRequest request, ServiceRequestStatus from, UUID actorUserId, UUID eventId) {
        ObjectNode payload = basePayload(request);
        payload.put("previousStatus", from.name());
        payload.put("newStatus", request.getStatus().name());
        if (actorUserId != null) {
            payload.put("actorUserId", actorUserId.toString());
        }
        enqueue(
                request,
                EVENT_STATUS_CHANGED,
                payload,
                "ServiceRequestStatusChanged:" + request.getId() + ":" + request.getStatus() + ":" + eventId);
    }

    public void publishDelivered(ServiceRequest request) {
        ObjectNode payload = basePayload(request);
        if (request.getDeliverableDocumentId() != null) {
            payload.put("deliverableDocumentId", request.getDeliverableDocumentId().toString());
        }
        enqueue(request, EVENT_DELIVERED, payload, "ServiceRequestDelivered:" + request.getId());
    }

    private ObjectNode basePayload(ServiceRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("requestId", request.getId().toString());
        payload.put("reference", request.getReference());
        payload.put("requestType", request.getRequestType().name());
        payload.put("studentId", request.getStudentId().toString());
        if (request.getAssignedTo() != null) {
            payload.put("assignedTo", request.getAssignedTo().toString());
        }
        return payload;
    }

    private void enqueue(ServiceRequest request, String eventType, ObjectNode payload, String idempotencyKey) {
        outboxWriter.enqueue(
                AGGREGATE_TYPE,
                request.getId(),
                eventType,
                payload.toString(),
                idempotencyKey);
    }
}
