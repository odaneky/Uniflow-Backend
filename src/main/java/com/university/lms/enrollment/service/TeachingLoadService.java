package com.university.lms.enrollment.service;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.dto.TeachingMeetingResponse;
import com.university.lms.course.dto.TeachingSectionResponse;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecturer teaching load with <em>live</em> seat counts from enrolments.
 *
 * <p>The course row's {@code enrolled_count} can drift from the roster (failed release, parallel
 * demo occurrences). Faculty UI must show who is actually enrolled, so this overlays the roster
 * count and repairs the counter when they disagree.
 */
@Service
@Transactional
public class TeachingLoadService {

    private final CourseCatalog courseCatalog;
    private final EnrollmentDirectory enrollmentDirectory;
    private final CurrentUserProvider currentUserProvider;

    public TeachingLoadService(
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            CurrentUserProvider currentUserProvider) {
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.currentUserProvider = currentUserProvider;
    }

    public List<TeachingSectionResponse> ownSections() {
        return courseCatalog.findSectionsTaughtBy(currentUserProvider.require().userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private TeachingSectionResponse toResponse(CourseCatalog.SectionSummary section) {
        int live = enrollmentDirectory.occupyingSeatCount(section.id());
        if (live != section.enrolledCount()) {
            enrollmentDirectory.reconcileSeatCount(section.id());
        }
        return new TeachingSectionResponse(
                section.id(),
                section.courseId(),
                section.courseCode(),
                courseCatalog
                        .findCourse(section.courseId())
                        .map(CourseCatalog.CourseSummary::title)
                        .orElse(section.courseCode()),
                section.sectionCode(),
                section.academicTermId(),
                section.lecturerUserId(),
                live,
                section.capacity(),
                courseCatalog.meetingsOf(section.id()).stream()
                        .map(m -> new TeachingMeetingResponse(
                                m.dayOfWeek(),
                                m.day(),
                                m.startTime(),
                                m.endTime(),
                                m.room(),
                                m.sessionType()))
                        .toList());
    }
}
