package com.university.lms.enrollment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.OutboxWriter;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.student.api.StudentDirectory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Enqueues enrolment lifecycle events for in-app notification dispatch. */
@Component
public class EnrollmentOutboxPublisher {

    public static final String EVENT_SECTION_CANCELLED = "EnrolmentCancelledBySectionCancellation";

    private static final Logger log = LoggerFactory.getLogger(EnrollmentOutboxPublisher.class);

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final CourseCatalog courseCatalog;
    private final StudentDirectory studentDirectory;

    public EnrollmentOutboxPublisher(
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            CourseCatalog courseCatalog,
            StudentDirectory studentDirectory) {
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.courseCatalog = courseCatalog;
        this.studentDirectory = studentDirectory;
    }

    /** The section itself is already cancelled by the time this is called — see {@code SectionCancellationService}. */
    public void publishSectionCancelled(Enrollment enrolment) {
        try {
            CourseCatalog.SectionSummary section =
                    courseCatalog.findSection(enrolment.getCourseSectionId()).orElse(null);
            UUID studentUserId = studentDirectory
                    .findById(enrolment.getStudentId())
                    .map(StudentDirectory.StudentSummary::userId)
                    .orElse(null);
            if (studentUserId == null) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enrollmentId", enrolment.getId().toString());
            payload.put("studentUserId", studentUserId.toString());
            if (section != null) {
                payload.put("courseCode", section.courseCode());
                if (section.courseTitle() != null && !section.courseTitle().isBlank()) {
                    payload.put("courseTitle", section.courseTitle());
                }
                if (section.sectionCode() != null) {
                    payload.put("sectionCode", section.sectionCode());
                }
            }
            outboxWriter.enqueue(
                    "ENROLLMENT",
                    enrolment.getId(),
                    EVENT_SECTION_CANCELLED,
                    objectMapper.writeValueAsString(payload),
                    "SectionCancelled:" + enrolment.getId());
        } catch (Exception ex) {
            log.warn("Could not enqueue section-cancellation notification for enrolment {}", enrolment.getId(), ex);
        }
    }
}
