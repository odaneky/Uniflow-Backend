package com.university.lms.grading.service;

import com.university.lms.common.exception.ApplicationException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.grading.dto.BulkGradeUploadResponse;
import com.university.lms.grading.dto.CreateGradeRequest;
import com.university.lms.grading.dto.GradeResponse;
import com.university.lms.student.api.StudentDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D8: a 200-student section entered one mark at a time is the difference between usable and
 * unusable. Each row goes through {@link GradeService#award} — the same entry point, and so the
 * same scale/band resolution, revision history, lock refusal and per-section authorization a single
 * award already has — with no new rules invented here.
 *
 * <p>{@code dryRun} checks only what is cheap to verify without attempting the real write: that the
 * student and the section exist. It cannot see a refusal that only the write path itself would
 * discover — a locked grade, a missing reason on a revision, a lecturer not authorized for that
 * particular section — so a clean dry run is a useful filter for the common mistakes (a mistyped
 * id, a wrong section), not a guarantee the real upload will succeed row for row.
 *
 * <p>Runs outside its own transaction ({@code NOT_SUPPORTED}), the same reason {@code
 * TermRolloverService} does: {@link GradeService#award} has its own {@code REQUIRED} transaction,
 * and with no ambient one to join, each row commits or fails independently — one bad row does not
 * roll back the marks already saved before it.
 */
@Service
public class BulkGradeUploadService {

    private final GradeService gradeService;
    private final StudentDirectory studentDirectory;
    private final CourseCatalog courseCatalog;

    public BulkGradeUploadService(
            GradeService gradeService, StudentDirectory studentDirectory, CourseCatalog courseCatalog) {
        this.gradeService = gradeService;
        this.studentDirectory = studentDirectory;
        this.courseCatalog = courseCatalog;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BulkGradeUploadResponse upload(List<CreateGradeRequest> requests, boolean dryRun) {
        List<BulkGradeUploadResponse.Row> rows = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;
        for (int i = 0; i < requests.size(); i++) {
            CreateGradeRequest request = requests.get(i);
            if (dryRun) {
                Optional<String> issue = precheck(request);
                if (issue.isPresent()) {
                    rows.add(new BulkGradeUploadResponse.Row(
                            i, request.studentId(), request.courseSectionId(), "WOULD_FAIL", issue.get()));
                    failed++;
                } else {
                    rows.add(new BulkGradeUploadResponse.Row(
                            i, request.studentId(), request.courseSectionId(), "WOULD_SUCCEED", null));
                    succeeded++;
                }
                continue;
            }
            try {
                GradeResponse saved = gradeService.award(request);
                rows.add(new BulkGradeUploadResponse.Row(
                        i, saved.studentId(), saved.courseSectionId(), "SAVED", null));
                succeeded++;
            } catch (ApplicationException ex) {
                rows.add(new BulkGradeUploadResponse.Row(
                        i, request.studentId(), request.courseSectionId(), "FAILED", ex.getMessage()));
                failed++;
            }
        }
        return new BulkGradeUploadResponse(dryRun, requests.size(), succeeded, failed, rows);
    }

    private Optional<String> precheck(CreateGradeRequest request) {
        if (!studentDirectory.exists(request.studentId())) {
            return Optional.of("No student exists with id " + request.studentId());
        }
        if (courseCatalog.findSection(request.courseSectionId()).isEmpty()) {
            return Optional.of("No course section exists with id " + request.courseSectionId());
        }
        return Optional.empty();
    }
}
