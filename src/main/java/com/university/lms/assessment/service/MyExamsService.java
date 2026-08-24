package com.university.lms.assessment.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.assessment.domain.ExamSitting;
import com.university.lms.assessment.domain.ExamSittingStatus;
import com.university.lms.assessment.dto.ExamSittingResponse;
import com.university.lms.assessment.dto.ExamTimetableResponse;
import com.university.lms.assessment.repository.ExamSittingRepository;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own exam timetable.
 *
 * <p>Derived from what they are enrolled in, never from a parameter. An exam timetable says where a
 * named person will be sitting at a known time, which is exactly the kind of thing that should not
 * be readable by asking for somebody else's student id.
 *
 * <p>Only <b>published</b> sittings are returned, and the filter is in the query rather than after
 * it: a draft timetable is wrong for most of its life, and a row that is never loaded cannot be
 * leaked by a later change to the response mapping.
 */
@Service
@Transactional(readOnly = true)
public class MyExamsService {

    /** A registration load is tens of sections; bounded so a self-service read cannot be unbounded. */
    private static final int MAX_SECTIONS = 200;

    private final ExamSittingRepository examSittingRepository;
    private final EnrollmentDirectory enrollmentDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final StudentDirectory studentDirectory;
    private final CourseCatalog courseCatalog;
    private final AcademicStructure academicStructure;

    public MyExamsService(
            ExamSittingRepository examSittingRepository,
            EnrollmentDirectory enrollmentDirectory,
            CurrentUserProvider currentUserProvider,
            StudentDirectory studentDirectory,
            CourseCatalog courseCatalog,
            AcademicStructure academicStructure) {
        this.examSittingRepository = examSittingRepository;
        this.enrollmentDirectory = enrollmentDirectory;
        this.currentUserProvider = currentUserProvider;
        this.studentDirectory = studentDirectory;
        this.courseCatalog = courseCatalog;
        this.academicStructure = academicStructure;
    }

    /**
     * Overlaps between the student's own papers.
     *
     * <p>Compared pairwise, which is fine at this size — a student sits a handful of exams, not
     * thousands. The list is already ordered by start time, so the pairs are reported in the order
     * the student will meet them.
     */
    private static List<ExamTimetableResponse.ExamClash> clashesAmong(List<ExamSittingResponse> exams) {
        List<ExamTimetableResponse.ExamClash> clashes = new ArrayList<>();
        for (int i = 0; i < exams.size(); i++) {
            for (int j = i + 1; j < exams.size(); j++) {
                ExamSittingResponse a = exams.get(i);
                ExamSittingResponse b = exams.get(j);
                if (a.startsAt().isBefore(b.endsAt()) && b.startsAt().isBefore(a.endsAt())) {
                    clashes.add(new ExamTimetableResponse.ExamClash(
                            a.id(),
                            b.id(),
                            "%s and %s are scheduled at the same time. Contact the examinations office."
                                    .formatted(a.courseCode(), b.courseCode())));
                }
            }
        }
        return clashes;
    }

    public ExamTimetableResponse ownTimetable() {
        UUID studentId = studentDirectory
                .studentIdOfUser(currentUserProvider.require().userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));

        // Through the enrolment module's published contract, not its repository. Reaching across a
        // module boundary into another module's tables is exactly what the api packages exist to
        // prevent, and this service was doing it.
        Set<UUID> sectionIds = new LinkedHashSet<>(enrollmentDirectory.accessibleSectionIds(studentId));

        if (sectionIds.isEmpty()) {
            return new ExamTimetableResponse(false, null, null, List.of(), List.of());
        }

        List<ExamSittingResponse> exams = new ArrayList<>();
        UUID termId = null;
        for (ExamSitting sitting :
                examSittingRepository.findByCourseSectionIdInAndPublishedTrueAndStatusOrderByStartsAtAsc(
                        sectionIds, ExamSittingStatus.SCHEDULED)) {
            var section = courseCatalog.findSection(sitting.getCourseSectionId());
            if (section.isEmpty()) {
                // The section vanished under a published sitting. Skip rather than fail: a student
                // checking their timetable should still see the rest of their exams.
                continue;
            }
            if (termId == null) {
                termId = section.get().academicTermId();
            }
            String title = courseCatalog
                    .findCourse(section.get().courseId())
                    .map(CourseCatalog.CourseSummary::title)
                    .orElse(section.get().courseCode());
            exams.add(ExamSittingResponse.from(
                    sitting, section.get().courseCode(), title, section.get().sectionCode()));
        }

        // Term taken from the sections themselves rather than from "the current term": a student
        // looking at their timetable cares about the exams they actually have.
        if (termId == null) {
            for (UUID sectionId : sectionIds) {
                var section = courseCatalog.findSection(sectionId);
                if (section.isPresent()) {
                    termId = section.get().academicTermId();
                    break;
                }
            }
        }

        List<ExamTimetableResponse.ExamClash> clashes = clashesAmong(exams);
        LocalDate today = LocalDate.now();
        return academicStructure
                .examPeriod(termId, today)
                .map(period -> new ExamTimetableResponse(
                        period.active(), period.startsOn(), period.endsOn(), exams, clashes))
                .orElseGet(() -> new ExamTimetableResponse(false, null, null, exams, clashes));
    }
}
