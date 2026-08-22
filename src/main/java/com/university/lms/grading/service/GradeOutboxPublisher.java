package com.university.lms.grading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.OutboxWriter;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.grading.domain.Grade;
import com.university.lms.student.api.StudentDirectory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Enqueues grade-published events for in-app notification dispatch. */
@Component
public class GradeOutboxPublisher {

    public static final String EVENT_PUBLISHED = "GradePublished";

    private static final Logger log = LoggerFactory.getLogger(GradeOutboxPublisher.class);

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final CourseCatalog courseCatalog;
    private final StudentDirectory studentDirectory;

    public GradeOutboxPublisher(
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            CourseCatalog courseCatalog,
            StudentDirectory studentDirectory) {
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.courseCatalog = courseCatalog;
        this.studentDirectory = studentDirectory;
    }

    public void publishPublished(Grade grade) {
        try {
            CourseCatalog.SectionSummary section =
                    courseCatalog.findSection(grade.getCourseSectionId()).orElse(null);
            String courseCode = section == null ? null : section.courseCode();
            String courseTitle = section == null ? null : section.courseTitle();
            UUID studentUserId = studentDirectory
                    .findById(grade.getStudentId())
                    .map(StudentDirectory.StudentSummary::userId)
                    .orElse(null);
            if (studentUserId == null) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("gradeId", grade.getId().toString());
            payload.put("studentUserId", studentUserId.toString());
            payload.put("courseSectionId", grade.getCourseSectionId().toString());
            if (courseCode != null) {
                payload.put("courseCode", courseCode);
            }
            if (courseTitle != null && !courseTitle.isBlank()) {
                payload.put("courseTitle", courseTitle);
            }
            if (section != null && section.sectionCode() != null) {
                payload.put("sectionCode", section.sectionCode());
            }
            String actionUrl = courseCode == null || courseCode.isBlank()
                    ? "/grades"
                    : "/courses/" + courseCode + "/grades";
            payload.put("actionUrl", actionUrl);
            outboxWriter.enqueue(
                    "GRADE",
                    grade.getId(),
                    EVENT_PUBLISHED,
                    objectMapper.writeValueAsString(payload),
                    "GradePublished:" + grade.getId());
        } catch (Exception ex) {
            log.warn("Could not enqueue grade published notification for {}", grade.getId(), ex);
        }
    }
}
