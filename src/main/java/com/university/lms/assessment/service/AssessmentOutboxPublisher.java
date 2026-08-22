package com.university.lms.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.assessment.domain.Assessment;
import com.university.lms.common.outbox.OutboxWriter;
import com.university.lms.course.api.CourseCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Enqueues assessment-published events for in-app notification dispatch. */
@Component
public class AssessmentOutboxPublisher {

    public static final String EVENT_PUBLISHED = "AssessmentPublished";

    private static final Logger log = LoggerFactory.getLogger(AssessmentOutboxPublisher.class);

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final CourseCatalog courseCatalog;

    public AssessmentOutboxPublisher(
            OutboxWriter outboxWriter, ObjectMapper objectMapper, CourseCatalog courseCatalog) {
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.courseCatalog = courseCatalog;
    }

    public void publishPublished(Assessment assessment) {
        try {
            CourseCatalog.SectionSummary section =
                    courseCatalog.findSection(assessment.getCourseSectionId()).orElse(null);
            String courseCode = section == null ? null : section.courseCode();
            String courseTitle = section == null ? null : section.courseTitle();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("assessmentId", assessment.getId().toString());
            payload.put("courseSectionId", assessment.getCourseSectionId().toString());
            payload.put("title", assessment.getTitle());
            payload.put("assessmentType", assessment.getAssessmentType().name());
            if (courseCode != null) {
                payload.put("courseCode", courseCode);
            }
            if (courseTitle != null && !courseTitle.isBlank()) {
                payload.put("courseTitle", courseTitle);
            }
            if (assessment.getDueAt() != null) {
                payload.put("dueAt", assessment.getDueAt().toString());
            }
            String pathKind = assessment.getAssessmentType().name().equals("QUIZ")
                            || assessment.getAssessmentType().name().equals("EXAM")
                    ? "/quiz/"
                    : "/assignments/";
            String actionUrl = courseCode == null || courseCode.isBlank()
                    ? "/courses"
                    : "/courses/" + courseCode + pathKind + assessment.getId();
            payload.put("actionUrl", actionUrl);

            outboxWriter.enqueue(
                    "ASSESSMENT",
                    assessment.getId(),
                    EVENT_PUBLISHED,
                    objectMapper.writeValueAsString(payload),
                    "AssessmentPublished:" + assessment.getId());
        } catch (Exception ex) {
            log.warn("Could not enqueue assessment published notification for {}", assessment.getId(), ex);
        }
    }
}
