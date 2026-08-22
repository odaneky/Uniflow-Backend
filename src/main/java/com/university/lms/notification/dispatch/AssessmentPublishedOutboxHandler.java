package com.university.lms.notification.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.assessment.service.AssessmentOutboxPublisher;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxEventHandler;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.service.NotificationDeliveryService;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Notifies enrolled students when an assessment is published. */
@Component
public class AssessmentPublishedOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final EnrollmentDirectory enrollmentDirectory;
    private final StudentDirectory studentDirectory;
    private final NotificationDeliveryService notificationDeliveryService;

    public AssessmentPublishedOutboxHandler(
            ObjectMapper objectMapper,
            EnrollmentDirectory enrollmentDirectory,
            StudentDirectory studentDirectory,
            NotificationDeliveryService notificationDeliveryService) {
        this.objectMapper = objectMapper;
        this.enrollmentDirectory = enrollmentDirectory;
        this.studentDirectory = studentDirectory;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    @Override
    public String eventType() {
        return AssessmentOutboxPublisher.EVENT_PUBLISHED;
    }

    @Override
    public void handle(DomainOutbox row) throws Exception {
        JsonNode payload = objectMapper.readTree(row.getPayload());
        UUID assessmentId = UUID.fromString(payload.get("assessmentId").asText());
        UUID sectionId = UUID.fromString(payload.get("courseSectionId").asText());
        String assessmentTitle = textOrNull(payload, "title");
        String assessmentType = textOrNull(payload, "assessmentType");
        String courseCode = textOrNull(payload, "courseCode");
        String courseTitle = textOrNull(payload, "courseTitle");
        String actionUrl = textOrNull(payload, "actionUrl");
        if (actionUrl == null && courseCode != null) {
            String pathKind = "QUIZ".equals(assessmentType) || "EXAM".equals(assessmentType) ? "/quiz/" : "/assignments/";
            actionUrl = "/courses/" + courseCode + pathKind + assessmentId;
        }

        String title;
        if (courseCode != null && courseTitle != null) {
            title = courseCode + " · " + courseTitle;
        } else if (courseCode != null) {
            title = courseCode + " · New assessment";
        } else {
            title = "New assessment";
        }

        String kindLabel = kindLabel(assessmentType);
        String body;
        if (assessmentTitle != null && !assessmentTitle.isBlank()) {
            body = kindLabel + " \"" + assessmentTitle + "\" is now available.";
        } else {
            body = "A new " + kindLabel.toLowerCase() + " is now available.";
        }

        Instant now = Instant.now();
        for (EnrollmentDirectory.SectionEnrolment enrolment : enrollmentDirectory.rosterOf(sectionId)) {
            if (!"ENROLLED".equalsIgnoreCase(enrolment.status())
                    && !"COMPLETED".equalsIgnoreCase(enrolment.status())) {
                continue;
            }
            UUID studentUserId = studentDirectory
                    .findById(enrolment.studentId())
                    .map(StudentDirectory.StudentSummary::userId)
                    .orElse(null);
            if (studentUserId == null) {
                continue;
            }
            Notification notification = new Notification(
                    studentUserId, NotificationType.ASSESSMENT_DUE, NotificationChannel.IN_APP, title, body);
            notification.assignSource("ASSESSMENT", assessmentId);
            if (actionUrl != null) {
                notification.assignActionUrl(actionUrl);
            }
            notification.markSent(now);
            notificationDeliveryService.deliverInApp(notification);
        }
    }

    private static String kindLabel(String assessmentType) {
        if (assessmentType == null) {
            return "Assessment";
        }
        return switch (assessmentType) {
            case "ASSIGNMENT" -> "Assignment";
            case "QUIZ" -> "Quiz";
            case "EXAM" -> "Exam";
            case "PROJECT" -> "Project";
            case "LAB" -> "Lab";
            case "PRESENTATION" -> "Presentation";
            default -> "Assessment";
        };
    }

    private static String textOrNull(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.get(field).asText() : null;
    }
}
