package com.university.lms.learning.service;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.learning.domain.CourseContent;
import com.university.lms.learning.domain.LearningErrorCode;
import com.university.lms.learning.domain.LearningMaterial;
import com.university.lms.learning.domain.LearningModule;
import com.university.lms.learning.domain.Lesson;
import com.university.lms.learning.dto.CourseContentResponse;
import com.university.lms.learning.dto.CreateLessonRequest;
import com.university.lms.learning.dto.CreateMaterialRequest;
import com.university.lms.learning.dto.CreateModuleRequest;
import com.university.lms.learning.dto.LearningMaterialResponse;
import com.university.lms.learning.dto.LearningModuleResponse;
import com.university.lms.learning.dto.LessonResponse;
import com.university.lms.learning.dto.UpsertContentRequest;
import com.university.lms.learning.repository.CourseContentRepository;
import com.university.lms.learning.repository.LearningMaterialRepository;
import com.university.lms.learning.repository.LearningModuleRepository;
import com.university.lms.learning.repository.LessonRepository;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teaching material for a course section.
 *
 * <p>Writes are restricted to the assigned lecturer and registry/faculty administration. Student
 * reads are published modules of sections they are enrolled in (or have completed), addressed by
 * {@code /me} so the student id never appears in the request.
 */
@Service
@Transactional(readOnly = true)
public class LearningService {

    private final CourseContentRepository contentRepository;
    private final LearningModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LearningMaterialRepository materialRepository;
    private final CourseCatalog courseCatalog;
    private final EnrollmentDirectory enrollmentDirectory;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final StaffAppointments staffAppointments;

    public LearningService(
            CourseContentRepository contentRepository,
            LearningModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            LearningMaterialRepository materialRepository,
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider,
            StaffAppointments staffAppointments) {
        this.contentRepository = contentRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.materialRepository = materialRepository;
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.studentDirectory = studentDirectory;
        this.currentUserProvider = currentUserProvider;
        this.staffAppointments = staffAppointments;
    }

    public CourseContentResponse ownContent(UUID sectionId) {
        requireEnrolledOrStaff(sectionId);
        return load(sectionId, !currentUserProvider.require().isStaff());
    }

    public CourseContentResponse staffContent(UUID sectionId) {
        requireTeacherOrAdmin(sectionId);
        return load(sectionId, false);
    }

    @Transactional
    public CourseContentResponse upsertContent(UUID sectionId, UpsertContentRequest request) {
        requireTeacherOrAdmin(sectionId);
        requireKnownSection(sectionId);
        CourseContent content = contentRepository
                .findByCourseSectionId(sectionId)
                .orElseGet(() -> contentRepository.save(new CourseContent(sectionId)));
        content.describe(request.overview());
        return load(sectionId, false);
    }

    @Transactional
    public LearningModuleResponse addModule(UUID sectionId, CreateModuleRequest request) {
        requireTeacherOrAdmin(sectionId);
        CourseContent content = contentRepository
                .findByCourseSectionId(sectionId)
                .orElseGet(() -> contentRepository.save(new CourseContent(sectionId)));
        int position = request.position() != null
                ? request.position()
                : moduleRepository.findByCourseContentIdOrderByPositionAsc(content.getId()).size();
        LearningModule module = new LearningModule(content, request.title(), position);
        if (Boolean.TRUE.equals(request.published())) {
            module.publish();
        }
        moduleRepository.save(module);
        return toModule(module);
    }

    @Transactional
    public LearningModuleResponse publishModule(UUID moduleId) {
        LearningModule module = requireModule(moduleId);
        requireTeacherOrAdmin(module.getCourseContent().getCourseSectionId());
        module.publish();
        return toModule(module);
    }

    @Transactional
    public LessonResponse addLesson(UUID moduleId, CreateLessonRequest request) {
        LearningModule module = requireModule(moduleId);
        requireTeacherOrAdmin(module.getCourseContent().getCourseSectionId());
        int position = request.position() != null
                ? request.position()
                : lessonRepository.findByLearningModuleIdOrderByPositionAsc(module.getId()).size();
        Lesson lesson = new Lesson(module, request.title(), position);
        lesson.summarise(request.summary());
        lessonRepository.save(lesson);
        return toLesson(lesson);
    }

    @Transactional
    public LearningMaterialResponse addMaterial(UUID lessonId, CreateMaterialRequest request) {
        Lesson lesson = requireLesson(lessonId);
        requireTeacherOrAdmin(lesson.getLearningModule().getCourseContent().getCourseSectionId());
        int position = request.position() != null
                ? request.position()
                : materialRepository.findByLessonIdOrderByPositionAsc(lesson.getId()).size();
        LearningMaterial material =
                new LearningMaterial(lesson, request.title(), request.materialType(), position);
        if (request.documentId() != null) {
            material.attach(request.documentId());
        } else if (request.externalUrl() != null) {
            material.linkTo(request.externalUrl());
        }
        materialRepository.save(material);
        return LearningMaterialResponse.from(material);
    }

    private CourseContentResponse load(UUID sectionId, boolean publishedOnly) {
        return contentRepository
                .findByCourseSectionId(sectionId)
                .map(content -> {
                    List<LearningModule> modules = publishedOnly
                            ? moduleRepository.findByCourseContentIdAndPublishedTrueOrderByPositionAsc(
                                    content.getId())
                            : moduleRepository.findByCourseContentIdOrderByPositionAsc(content.getId());
                    return CourseContentResponse.from(
                            content, modules.stream().map(this::toModule).toList());
                })
                .orElseGet(() -> CourseContentResponse.empty(sectionId));
    }

    private LearningModuleResponse toModule(LearningModule module) {
        List<LessonResponse> lessons = lessonRepository
                .findByLearningModuleIdOrderByPositionAsc(module.getId())
                .stream()
                .map(this::toLesson)
                .toList();
        return LearningModuleResponse.from(module, lessons);
    }

    private LessonResponse toLesson(Lesson lesson) {
        List<LearningMaterialResponse> materials = materialRepository
                .findByLessonIdOrderByPositionAsc(lesson.getId())
                .stream()
                .map(LearningMaterialResponse::from)
                .toList();
        return LessonResponse.from(lesson, materials);
    }

    private LearningModule requireModule(UUID moduleId) {
        return moduleRepository
                .findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        LearningErrorCode.LEARNING_MODULE_NOT_FOUND, "No learning module exists with id " + moduleId));
    }

    private Lesson requireLesson(UUID lessonId) {
        return lessonRepository
                .findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        LearningErrorCode.LEARNING_LESSON_NOT_FOUND, "No lesson exists with id " + lessonId));
    }

    private void requireKnownSection(UUID sectionId) {
        courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        LearningErrorCode.LEARNING_SECTION_NOT_FOUND,
                        "No course section exists with id " + sectionId));
    }

    private void requireTeacherOrAdmin(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        LearningErrorCode.LEARNING_SECTION_NOT_FOUND,
                        "No course section exists with id " + sectionId));
        if (isAuthorizedAdmin(caller, section)) {
            return;
        }
        if (caller.hasRole(SecurityRoles.LECTURER) && caller.userId().equals(section.lecturerUserId())) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to change this section");
    }

    /**
     * A5: SYSTEM_ADMIN/REGISTRAR/FACULTY_ADMIN previously bypassed section-department scoping
     * unconditionally here — the write-side twin of the over-reach {@link #isAuthorizedStaff}
     * already fixed on the read side, missed because this guard has a different name and a
     * narrower role set. Deliberately a separate helper rather than reusing {@code
     * isAuthorizedStaff}: that one authorizes any staff role including LECTURER by department
     * alone, which would wrongly let a lecturer edit a section they do not teach as long as it is
     * in their department — the LECTURER branch below must stay gated on being this section's
     * lecturer specifically, unchanged. Same fail-open resolution and SYSTEM_ADMIN carve-out as
     * {@code isAuthorizedStaff} otherwise.
     */
    private boolean isAuthorizedAdmin(CurrentUser caller, CourseCatalog.SectionSummary section) {
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN))) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return true;
        }
        if (staffAppointments.activeAppointmentsOf(caller.userId()).isEmpty()) {
            return true;
        }
        Optional<UUID> orgUnitId = courseCatalog
                .departmentOfCourse(section.courseId())
                .flatMap(departmentId -> staffAppointments.orgUnitFor("DEPARTMENT", departmentId));
        return orgUnitId.isEmpty() || staffAppointments.isAppointedOver(caller.userId(), orgUnitId.get());
    }

    private void requireEnrolledOrStaff(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        LearningErrorCode.LEARNING_SECTION_NOT_FOUND, "No course section exists with id " + sectionId));
        if (isAuthorizedStaff(caller, section)) {
            return;
        }
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        if (!enrollmentDirectory.canAccessLearning(studentId, sectionId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }

    /**
     * A5: the first guard narrowed from a blind {@code isStaff()} to an org-scoped check, without a
     * flag day. {@code SYSTEM_ADMIN} always passes, matching every other guard's broad-role
     * carve-out. Everyone else needs an active appointment <em>and</em> — only once the section's
     * department has actually been mirrored as an org unit — to be appointed over it.
     *
     * <p>Fails open (returns {@code true}, exactly {@code isStaff()}'s old behaviour) at each
     * "not yet provisioned" branch: caller has no appointment at all, or the section's course has
     * no department, or that department has no linked org unit. None of those can happen mid-flight
     * from a bug — they happen when an environment has not yet run {@code
     * POST /staff-appointments/reconcile} and {@code POST /faculties/reconcile-org-units}, or when a
     * department has not been individually re-appointed to. This is deliberate: restricting access
     * ahead of that data actually existing would lock out every lecturer the day this ships.
     */
    private boolean isAuthorizedStaff(CurrentUser caller, CourseCatalog.SectionSummary section) {
        if (!caller.isStaff()) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return true;
        }
        if (staffAppointments.activeAppointmentsOf(caller.userId()).isEmpty()) {
            return true;
        }
        Optional<UUID> orgUnitId = courseCatalog
                .departmentOfCourse(section.courseId())
                .flatMap(departmentId -> staffAppointments.orgUnitFor("DEPARTMENT", departmentId));
        return orgUnitId.isEmpty() || staffAppointments.isAppointedOver(caller.userId(), orgUnitId.get());
    }
}
