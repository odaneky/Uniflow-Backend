package com.university.lms.course.service;

import com.university.lms.common.exception.ApplicationException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.domain.CourseSectionStatus;
import com.university.lms.course.domain.SectionComponent;
import com.university.lms.course.dto.CourseSectionResponse;
import com.university.lms.course.dto.CreateCourseSectionRequest;
import com.university.lms.course.dto.SectionComponentRequest;
import com.university.lms.course.dto.TermRolloverResponse;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.course.repository.SectionComponentRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D7: the most repeated administrative operation of the year — carrying a term's sections forward
 * into the next one — was entirely manual, section by section. This copies each source section's
 * course, capacity, lecturer and component breakdown into the target term through {@link
 * CourseService#addSection}, the same entry point a registrar would use by hand, so every rule that
 * already governs creating a section (offerability, term validity, duplicate-code refusal) applies
 * here unchanged rather than being re-implemented.
 *
 * <p>Meeting times and rooms are deliberately not carried over: those are genuinely new each term,
 * and copying stale ones would be worse than requiring them to be entered — the same reasoning
 * {@code SectionCancellationService} gives for not attempting its own out-of-scope recheck.
 *
 * <p>Idempotent by an explicit check, not by relying on {@code addSection}'s own duplicate-code
 * refusal: a new section's code is auto-generated fresh every call ({@code course_sections} has no
 * per-term uniqueness left — V29 made section codes unique per course, not per course-and-term —
 * so a second run would never collide on a code and would silently double up every course that
 * already succeeded). A course already offered in the target term is skipped instead.
 *
 * <p>{@code rollover} itself runs outside any transaction ({@code NOT_SUPPORTED}) rather than the
 * usual class-level {@code readOnly = true}: a class-level default still applies to a method with
 * no override of its own, so simply omitting {@code @Transactional} here would not have been
 * enough — {@link CourseService#addSection}'s {@code REQUIRED} propagation would have joined that
 * inherited read-only transaction and failed every insert. With no ambient transaction to join,
 * each {@code addSection} call opens its own, so one section's failure — a cancelled course, a
 * validation error specific to that section — does not roll back the sections already created
 * before it, and nothing here ever needs a read-only connection of its own.
 */
@Service
public class TermRolloverService {

    private final CourseSectionRepository courseSectionRepository;
    private final SectionComponentRepository sectionComponentRepository;
    private final CourseService courseService;
    private final CurrentUserProvider currentUserProvider;

    public TermRolloverService(
            CourseSectionRepository courseSectionRepository,
            SectionComponentRepository sectionComponentRepository,
            CourseService courseService,
            CurrentUserProvider currentUserProvider) {
        this.courseSectionRepository = courseSectionRepository;
        this.sectionComponentRepository = sectionComponentRepository;
        this.courseService = courseService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TermRolloverResponse rollover(UUID sourceTermId, UUID targetTermId, boolean dryRun) {
        requireRegistry();
        List<CourseSection> sourceSections = courseSectionRepository.findByAcademicTermId(sourceTermId);
        List<TermRolloverResponse.Row> rows = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (CourseSection source : sourceSections) {
            UUID courseId = source.getCourse().getId();
            String courseCode = source.getCourse().getCourseCode();
            if (source.getStatus() == CourseSectionStatus.CANCELLED) {
                rows.add(new TermRolloverResponse.Row(
                        courseCode, source.getSectionCode(), null, "SKIPPED", "Source section was cancelled"));
                skipped++;
                continue;
            }
            // Idempotency: a section code is auto-generated fresh every time (course_sections has
            // no per-term uniqueness left — V29 made it course-wide), so without this check a
            // second rollover run would not fail as a duplicate, it would silently double up every
            // course that already succeeded the first time.
            if (!courseSectionRepository.findByCourseIdAndAcademicTermId(courseId, targetTermId).isEmpty()) {
                rows.add(new TermRolloverResponse.Row(
                        courseCode, source.getSectionCode(), null, "SKIPPED", "Already rolled over to the target term"));
                skipped++;
                continue;
            }
            if (dryRun) {
                rows.add(new TermRolloverResponse.Row(
                        courseCode, source.getSectionCode(), null, "WOULD_CREATE", null));
                created++;
                continue;
            }
            List<SectionComponentRequest> offerings = sectionComponentRepository.findBySectionId(source.getId()).stream()
                    .map(component -> new SectionComponentRequest(
                            component.getComponent(), component.getCapacity(), component.getLecturerUserId()))
                    .toList();
            try {
                CourseSectionResponse createdSection = courseService.addSection(
                        courseId,
                        new CreateCourseSectionRequest(
                                targetTermId, null, null, source.getCapacity(), source.getLecturerUserId(), offerings));
                rows.add(new TermRolloverResponse.Row(
                        courseCode, source.getSectionCode(), createdSection.sectionCode(), "CREATED", null));
                created++;
            } catch (ApplicationException ex) {
                rows.add(new TermRolloverResponse.Row(
                        courseCode, source.getSectionCode(), null, "FAILED", ex.getMessage()));
                failed++;
            }
        }
        return new TermRolloverResponse(
                sourceTermId, targetTermId, dryRun, sourceSections.size(), created, skipped, failed, rows);
    }

    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "You do not have permission to roll over a term");
        }
    }
}
