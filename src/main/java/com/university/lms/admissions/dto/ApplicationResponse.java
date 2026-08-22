package com.university.lms.admissions.dto;

import com.university.lms.admissions.domain.Application;
import com.university.lms.admissions.domain.ApplicationEvent;
import com.university.lms.admissions.domain.ApplicationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String reference,
        String applicantEmail,
        String applicantName,
        UUID programmeId,
        UUID academicTermId,
        ApplicationStatus status,
        Map<String, Object> payload,
        BigDecimal depositAmount,
        Instant depositPaidAt,
        UUID studentId,
        UUID assignedTo,
        String assignedToName,
        String decisionNote,
        UUID decidedBy,
        String decidedByName,
        Instant submittedAt,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt,
        List<UUID> documentIds,
        List<EventStep> events) {

    public record EventStep(
            UUID id,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            UUID actorUserId,
            String actorName,
            String note,
            Instant at) {}

    public static ApplicationResponse from(
            Application application,
            String assignedToName,
            String decidedByName,
            Map<String, Object> payloadMap,
            List<UUID> documentIds,
            List<EventStep> eventSteps) {
        return new ApplicationResponse(
                application.getId(),
                application.getReference(),
                application.getApplicantEmail(),
                application.getApplicantName(),
                application.getProgrammeId(),
                application.getAcademicTermId(),
                application.getStatus(),
                payloadMap,
                application.getDepositAmount(),
                application.getDepositPaidAt(),
                application.getStudentId(),
                application.getAssignedTo(),
                assignedToName,
                application.getDecisionNote(),
                application.getDecidedBy(),
                decidedByName,
                application.getSubmittedAt(),
                application.getDecidedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                documentIds,
                eventSteps);
    }

    public static List<EventStep> eventSteps(List<ApplicationEvent> history, java.util.function.Function<UUID, String> nameOf) {
        return history.stream()
                .map(event -> new EventStep(
                        event.getId(),
                        event.getFromStatus(),
                        event.getToStatus(),
                        event.getActorUserId(),
                        nameOf.apply(event.getActorUserId()),
                        event.getNote(),
                        event.getCreatedAt()))
                .toList();
    }
}
