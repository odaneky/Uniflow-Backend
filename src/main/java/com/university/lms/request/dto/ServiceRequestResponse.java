package com.university.lms.request.dto;

import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestEvent;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ServiceRequestResponse(
        UUID id,
        UUID studentId,
        String studentNumber,
        String studentName,
        ServiceRequestType type,
        String typeLabel,
        ServiceRequestStatus status,
        String reference,
        String note,
        Map<String, Object> payload,
        UUID assignedTo,
        String assignedToName,
        String decisionNote,
        UUID decidedBy,
        String decidedByName,
        UUID deliverableDocumentId,
        Instant submittedAt,
        Instant updatedAt,
        Instant decidedAt,
        Instant fulfilledAt,
        String fulfillmentError,
        List<EventStep> events,
        List<TimelineStep> timeline) {

    public record EventStep(
            UUID id,
            ServiceRequestStatus fromStatus,
            ServiceRequestStatus toStatus,
            UUID actorUserId,
            String actorName,
            String note,
            Instant at) {}

    public record TimelineStep(String label, Instant at, boolean done, boolean current) {}

    public static ServiceRequestResponse from(
            ServiceRequest request,
            String studentNumber,
            String studentName,
            String decidedByName,
            String assignedToName,
            List<ServiceRequestEvent> history,
            Map<String, Object> payloadMap,
            List<EventStep> eventSteps) {
        return new ServiceRequestResponse(
                request.getId(),
                request.getStudentId(),
                studentNumber,
                studentName,
                request.getRequestType(),
                request.getRequestType().displayName(),
                request.getStatus(),
                request.getReference(),
                request.getNote(),
                payloadMap,
                request.getAssignedTo(),
                assignedToName,
                request.getDecisionNote(),
                request.getDecidedBy(),
                decidedByName,
                request.getDeliverableDocumentId(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getDecidedAt(),
                request.getFulfilledAt(),
                request.getFulfillmentError(),
                eventSteps,
                timeline(request, history));
    }

    static List<TimelineStep> timeline(ServiceRequest request, List<ServiceRequestEvent> history) {
        if (!history.isEmpty()) {
            return buildFromHistory(request, history);
        }
        return legacyTimeline(request);
    }

    private static List<TimelineStep> buildFromHistory(ServiceRequest request, List<ServiceRequestEvent> history) {
        String review = request.getRequestType().reviewStep();
        Instant submitted = request.getCreatedAt();
        Instant reviewAt = history.stream()
                .filter(e -> e.getToStatus() == ServiceRequestStatus.IN_REVIEW)
                .map(ServiceRequestEvent::getCreatedAt)
                .findFirst()
                .orElse(null);
        Instant decisionAt = history.stream()
                .filter(e -> e.getToStatus() == ServiceRequestStatus.APPROVED
                        || e.getToStatus() == ServiceRequestStatus.DENIED
                        || e.getToStatus() == ServiceRequestStatus.CANCELLED)
                .map(ServiceRequestEvent::getCreatedAt)
                .findFirst()
                .orElse(null);
        Instant completedAt = history.stream()
                .filter(e -> e.getToStatus() == ServiceRequestStatus.COMPLETED)
                .map(ServiceRequestEvent::getCreatedAt)
                .findFirst()
                .orElse(null);
        return switch (request.getStatus()) {
            case SUBMITTED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, null, false, true),
                    new TimelineStep("Decision", null, false, false),
                    new TimelineStep("Completed", null, false, false));
            case IN_REVIEW -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, reviewAt, true, true),
                    new TimelineStep("Decision", null, false, false),
                    new TimelineStep("Completed", null, false, false));
            case APPROVED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, reviewAt, true, false),
                    new TimelineStep("Approved", decisionAt, true, true),
                    new TimelineStep("Completed", null, false, false));
            case COMPLETED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, reviewAt, true, false),
                    new TimelineStep("Approved", decisionAt, true, false),
                    new TimelineStep("Completed", completedAt, true, false));
            case DENIED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, reviewAt, true, false),
                    new TimelineStep("Denied", decisionAt, true, false));
            case CANCELLED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep("Cancelled", decisionAt, true, false));
        };
    }

    private static List<TimelineStep> legacyTimeline(ServiceRequest request) {
        Instant submitted = request.getCreatedAt();
        Instant decided = request.getDecidedAt();
        String review = request.getRequestType().reviewStep();
        return switch (request.getStatus()) {
            case SUBMITTED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, null, false, true),
                    new TimelineStep("Decision", null, false, false),
                    new TimelineStep("Completed", null, false, false));
            case IN_REVIEW -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, decided, true, true),
                    new TimelineStep("Decision", null, false, false),
                    new TimelineStep("Completed", null, false, false));
            case APPROVED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, decided, true, false),
                    new TimelineStep("Approved", decided, true, true),
                    new TimelineStep("Completed", null, false, false));
            case COMPLETED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, decided, true, false),
                    new TimelineStep("Approved", decided, true, false),
                    new TimelineStep("Completed", decided, true, false));
            case DENIED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, decided, true, false),
                    new TimelineStep("Denied", decided, true, false));
            case CANCELLED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep("Cancelled", decided, true, false));
        };
    }
}
