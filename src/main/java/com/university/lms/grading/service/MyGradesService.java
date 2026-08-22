package com.university.lms.grading.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.dto.GradeResponse;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own grades.
 *
 * <p>Two rules, both load-bearing.
 *
 * <p><b>Only published grades.</b> A grade exists from the moment a marker records it, but it is
 * not a result until it has been released. Serving unpublished marks would leak provisional
 * decisions and pre-empt moderation, so the filter is applied in the query rather than after it —
 * an unpublished row is never loaded, so it cannot be leaked by a later refactor of the mapping.
 *
 * <p><b>The student is the caller.</b> The student id comes from the authenticated principal, never
 * from a parameter. Grades are among the most sensitive records the university holds.
 */
@Service
@Transactional(readOnly = true)
public class MyGradesService {

    private final GradeRepository gradeRepository;
    private final CurrentUserProvider currentUserProvider;
    private final StudentDirectory studentDirectory;
    private final CourseCatalog courseCatalog;
    private final AcademicStructure academicStructure;
    private final EnrollmentDirectory enrollmentDirectory;

    public MyGradesService(
            GradeRepository gradeRepository,
            CurrentUserProvider currentUserProvider,
            StudentDirectory studentDirectory,
            CourseCatalog courseCatalog,
            AcademicStructure academicStructure,
            EnrollmentDirectory enrollmentDirectory) {
        this.gradeRepository = gradeRepository;
        this.currentUserProvider = currentUserProvider;
        this.studentDirectory = studentDirectory;
        this.courseCatalog = courseCatalog;
        this.academicStructure = academicStructure;
        this.enrollmentDirectory = enrollmentDirectory;
    }

    public PageResponse<GradeResponse> ownGrades(Pageable pageable) {
        CurrentUser caller = currentUserProvider.require();
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));

        return PageResponse.from(
                gradeRepository.findByStudentIdAndPublishedTrue(studentId, pageable),
                grade -> toResponse(studentId, grade));
    }

    private GradeResponse toResponse(UUID studentId, Grade grade) {
        return courseCatalog
                .findSection(grade.getCourseSectionId())
                .map(section -> {
                    CourseCatalog.CourseSummary course =
                            courseCatalog.findCourse(section.courseId()).orElse(null);
                    Integer credits = course == null ? null : course.credits();
                    Integer level = course == null ? null : course.level();
                    AcademicStructure.TermSummary term = academicStructure
                            .findTerm(section.academicTermId(), Instant.now())
                            .orElse(null);
                    Integer attempt = enrollmentDirectory
                            .attemptNumberOf(studentId, grade.getCourseSectionId())
                            .orElseGet(() -> fallbackAttempt(studentId, section.courseId(), grade));
                    return GradeResponse.from(
                            grade,
                            section.courseCode(),
                            section.courseTitle(),
                            credits,
                            term == null ? null : term.academicYearCode(),
                            term == null ? null : term.name(),
                            level,
                            attempt);
                })
                .orElseGet(() -> GradeResponse.from(grade));
    }

    /** When enrolment is missing, rank this overall among published overalls for the course. */
    private Integer fallbackAttempt(UUID studentId, UUID courseId, Grade grade) {
        if (grade.getAssessmentId() != null) {
            return null;
        }
        List<Grade> sameCourse = gradeRepository.findAllByStudentIdAndPublishedTrue(studentId).stream()
                .filter(g -> g.getAssessmentId() == null)
                .filter(g -> courseCatalog
                        .findSection(g.getCourseSectionId())
                        .map(s -> s.courseId().equals(courseId))
                        .orElse(false))
                .sorted(Comparator.comparing(Grade::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        for (int i = 0; i < sameCourse.size(); i++) {
            if (sameCourse.get(i).getId().equals(grade.getId())) {
                return i + 1;
            }
        }
        return 1;
    }
}
