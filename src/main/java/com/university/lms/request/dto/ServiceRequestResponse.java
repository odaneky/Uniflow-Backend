package com.university.lms.request.dto;

import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import java.time.Instant;
import java.util.List;
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
        String decisionNote,
        UUID decidedBy,
        String decidedByName,
        Instant submittedAt,
        Instant updatedAt,
        Instant decidedAt,
        List<TimelineStep> timeline) {

    public record TimelineStep(String label, Instant at, boolean done, boolean current) {}

    public static ServiceRequestResponse from(
            ServiceRequest request, String studentNumber, String studentName, String decidedByName) {
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
                request.getDecisionNote(),
                request.getDecidedBy(),
                decidedByName,
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getDecidedAt(),
                timeline(request));
    }

    static List<TimelineStep> timeline(ServiceRequest request) {
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
                    new TimelineStep("Decision", decided, true, false),
                    new TimelineStep("Completed", decided, true, false));
            case DENIED -> List.of(
                    new TimelineStep("Submitted", submitted, true, false),
                    new TimelineStep(review, decided, true, false),
                    new TimelineStep("Denied", decided, true, false));
        };
    }
}
