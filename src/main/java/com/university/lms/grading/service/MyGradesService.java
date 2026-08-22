package com.university.lms.grading.service;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.grading.dto.GradeResponse;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
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

    public MyGradesService(
            GradeRepository gradeRepository,
            CurrentUserProvider currentUserProvider,
            StudentDirectory studentDirectory) {
        this.gradeRepository = gradeRepository;
        this.currentUserProvider = currentUserProvider;
        this.studentDirectory = studentDirectory;
    }

    public PageResponse<GradeResponse> ownGrades(Pageable pageable) {
        CurrentUser caller = currentUserProvider.require();
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));

        return PageResponse.from(
                gradeRepository.findByStudentIdAndPublishedTrue(studentId, pageable), GradeResponse::from);
    }
}
