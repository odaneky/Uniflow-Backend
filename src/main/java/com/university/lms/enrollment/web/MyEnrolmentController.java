package com.university.lms.enrollment.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.enrollment.dto.CheckoutEnrollmentsResponse;
import com.university.lms.enrollment.dto.EnrollmentResponse;
import com.university.lms.enrollment.dto.MyCourseResponse;
import com.university.lms.enrollment.dto.RegistrationContextResponse;
import com.university.lms.enrollment.service.EnrollmentService;
import com.university.lms.enrollment.service.MyEnrolmentService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own enrolments.
 *
 * <p>The student is never named in the request. {@code GET /api/v1/enrollments?studentId=...} exists
 * for staff and is ownership-checked, but a student portal should use these paths, where the subject
 * is structurally impossible to get wrong.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MyEnrolmentController {

    private final MyEnrolmentService myEnrolmentService;
    private final EnrollmentService enrollmentService;

    public MyEnrolmentController(MyEnrolmentService myEnrolmentService, EnrollmentService enrollmentService) {
        this.myEnrolmentService = myEnrolmentService;
        this.enrollmentService = enrollmentService;
    }

    /** Everything the caller has ever been enrolled in, including dropped and completed records. */
    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/registration")
    public PageResponse<EnrollmentResponse> registration(@PageableDefault(size = 20) Pageable pageable) {
        return myEnrolmentService.ownEnrolments(pageable);
    }

    /**
     * The courses the caller is currently taking, with catalog detail attached.
     *
     * @param academicTermId optional filter; a portal usually wants the current term only
     */
    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/courses")
    public List<MyCourseResponse> courses(@RequestParam(required = false) UUID academicTermId) {
        return myEnrolmentService.ownCourses(academicTermId);
    }

    /**
     * Term, phase, and which registration actions the caller may take right now. The student is
     * never named — this is the portal's source of truth for add/drop vs withdraw.
     */
    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/registration-context")
    public RegistrationContextResponse registrationContext() {
        return myEnrolmentService.ownRegistrationContext();
    }

    /**
     * Undoes the caller's last confirmed cart while the term window and the university correction
     * hours are both still open. The student is never named.
     */
    @PostMapping("/registration/checkouts/{batchId}/undo")
    @AccessClass(OWN_RECORD_ONLY)
    public CheckoutEnrollmentsResponse undoCheckout(@PathVariable UUID batchId) {
        return enrollmentService.undoOwnCheckout(batchId);
    }
}
