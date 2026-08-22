package com.university.lms.course.dto;

import com.university.lms.course.domain.CourseComponent;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.domain.CourseSectionStatus;
import com.university.lms.course.domain.SectionMeeting;
import java.util.List;
import java.util.UUID;

/** Representation of a course offering, including live seat availability. */
public record CourseSectionResponse(
        UUID id,
        UUID courseId,
        String courseCode,
        UUID academicTermId,
        String sectionCode,
        CourseComponent component,
        UUID lecturerUserId,
        String lecturerName,
        int capacity,
        int enrolledCount,
        int availableSeats,
        CourseSectionStatus status,
        List<SectionMeetingResponse> meetings,
        List<SectionComponentResponse> components) {

    public static CourseSectionResponse from(
            CourseSection section,
            String lecturerName,
            List<SectionMeeting> meetings,
            List<SectionComponentResponse> components) {
        return new CourseSectionResponse(
                section.getId(),
                section.getCourse().getId(),
                section.getCourse().getCourseCode(),
                section.getAcademicTermId(),
                section.getSectionCode(),
                section.getComponent(),
                section.getLecturerUserId(),
                lecturerName,
                section.getCapacity(),
                section.getEnrolledCount(),
                section.availableSeats(),
                section.getStatus(),
                meetings.stream()
                        .map(row -> new SectionMeetingResponse(
                                row.getId(),
                                row.getDayOfWeek(),
                                SectionMeetingResponse.dayName(row.getDayOfWeek()),
                                row.getStartTime(),
                                row.getEndTime(),
                                row.getRoom(),
                                row.getSessionType()))
                        .toList(),
                components);
    }
}
