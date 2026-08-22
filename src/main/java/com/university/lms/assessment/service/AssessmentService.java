package com.university.lms.assessment.service;

import com.university.lms.assessment.domain.Assessment;
import com.university.lms.assessment.domain.AssessmentAttempt;
import com.university.lms.assessment.domain.AssessmentErrorCode;
import com.university.lms.assessment.domain.AssessmentType;
import com.university.lms.assessment.dto.AssessmentResponse;
import com.university.lms.assessment.dto.AttemptResponse;
import com.university.lms.assessment.dto.CreateAssessmentRequest;
import com.university.lms.assessment.repository.AssessmentAttemptRepository;
import com.university.lms.assessment.repository.AssessmentRepository;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Assessed work set on a course section. Marks live in the grading module, not here. */
@Service
@Transactional(readOnly = true)
public class AssessmentService {

    private static final long MAX_UPLOAD_BYTES = 12_582_912L;
    private static final Set<AssessmentType> FILE_BASED =
            Set.of(AssessmentType.ASSIGNMENT, AssessmentType.PROJECT, AssessmentType.LAB, AssessmentType.PRESENTATION);

    private final AssessmentRepository assessmentRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final CourseCatalog courseCatalog;
    private final EnrollmentDirectory enrollmentDirectory;
    private final StudentDirectory studentDirectory;
    private final UserDirectory userDirectory;
    private final DocumentStore documentStore;
    private final CurrentUserProvider currentUserProvider;
    private final StudentBilling studentBilling;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            AssessmentAttemptRepository attemptRepository,
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            StudentDirectory studentDirectory,
            UserDirectory userDirectory,
            DocumentStore documentStore,
            CurrentUserProvider currentUserProvider,
            StudentBilling studentBilling) {
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.studentDirectory = studentDirectory;
        this.userDirectory = userDirectory;
        this.documentStore = documentStore;
        this.currentUserProvider = currentUserProvider;
        this.studentBilling = studentBilling;
    }

    public List<AssessmentResponse> own(UUID sectionId) {
        requireEnrolledOrStaff(sectionId);
        boolean publishedOnly = !currentUserProvider.require().isStaff();
        List<Assessment> rows = publishedOnly
                ? assessmentRepository.findByCourseSectionIdAndPublishedTrue(sectionId)
                : assessmentRepository.findByCourseSectionId(sectionId);
        boolean examBlocked = publishedOnly && examsBlockedForSection(sectionId);
        return rows.stream().map(row -> AssessmentResponse.from(row, examBlocked)).toList();
    }

    public List<AssessmentResponse> forSection(UUID sectionId) {
        requireTeacherOrAdmin(sectionId);
        return assessmentRepository.findByCourseSectionId(sectionId).stream()
                .map(AssessmentResponse::from)
                .toList();
    }

    @Transactional
    public AssessmentResponse create(UUID sectionId, CreateAssessmentRequest request) {
        requireTeacherOrAdmin(sectionId);
        requireKnownSection(sectionId);
        Assessment assessment = new Assessment(
                sectionId, request.title(), request.assessmentType(), request.maxScore(), request.weightPercent());
        if (request.instructions() != null) {
            assessment.describe(request.instructions());
        }
        if (request.dueAt() != null) {
            assessment.schedule(request.dueAt());
        }
        if (Boolean.TRUE.equals(request.published())) {
            assessment.publish();
        }
        if (request.durationMinutes() != null) {
            assessment.setDurationMinutes(request.durationMinutes());
        }
        if (request.passMarkPercent() != null) {
            assessment.setPassMarkPercent(request.passMarkPercent());
        }
        return AssessmentResponse.from(assessmentRepository.save(assessment));
    }

    @Transactional
    public AssessmentResponse publish(UUID assessmentId) {
        Assessment assessment = assessmentRepository
                .findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ASSESSMENT_NOT_FOUND, "No assessment exists with id " + assessmentId));
        requireTeacherOrAdmin(assessment.getCourseSectionId());
        assessment.publish();
        return AssessmentResponse.from(assessment);
    }

    public List<AttemptResponse> ownAttempts(UUID assessmentId) {
        Assessment assessment = requireAssessment(assessmentId);
        UUID studentId = requireEnrolledStudent(assessment.getCourseSectionId());
        if (!currentUserProvider.require().isStaff() && !assessment.isPublished()) {
            throw notFound(assessmentId);
        }
        return attemptRepository
                .findByAssessmentIdAndStudentIdOrderByAttemptNumberAsc(assessmentId, studentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AttemptResponse> attemptsFor(UUID assessmentId) {
        Assessment assessment = requireAssessment(assessmentId);
        requireTeacherOrAdmin(assessment.getCourseSectionId());
        return attemptRepository.findByAssessmentIdWithAssessment(assessmentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AttemptResponse submitOwn(UUID assessmentId, MultipartFile file) {
        Assessment assessment = requireAssessment(assessmentId);
        UUID studentId = requireEnrolledStudent(assessment.getCourseSectionId());
        if (!assessment.isPublished()) {
            throw notFound(assessmentId);
        }
        refuseIfExamBlocked(assessment, studentId);
        if (!FILE_BASED.contains(assessment.getAssessmentType())) {
            throw new BusinessException(
                    AssessmentErrorCode.ATTEMPT_NOT_FILE_BASED, "This assessment is not submitted as a file");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(AssessmentErrorCode.ATTEMPT_FILE_REQUIRED, "A file is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(
                    AssessmentErrorCode.ATTEMPT_FILE_TOO_LARGE, "File must be at most 12 MB");
        }
        String originalName = file.getOriginalFilename() == null ? "submission.bin" : file.getOriginalFilename();
        if (!allowedFile(file.getContentType(), originalName)) {
            throw new BusinessException(
                    AssessmentErrorCode.ATTEMPT_FILE_TYPE_NOT_ALLOWED, "Only PDF or ZIP files are accepted");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new BusinessException(AssessmentErrorCode.ATTEMPT_FILE_REQUIRED, "A file is required");
        }
        CurrentUser caller = currentUserProvider.require();
        DocumentStore.StoredFile stored = documentStore.store(
                caller.userId(),
                "ASSESSMENT_SUBMISSION",
                originalName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                bytes);
        int next = attemptRepository
                        .findByAssessmentIdAndStudentIdOrderByAttemptNumberAsc(assessmentId, studentId)
                        .stream()
                        .mapToInt(AssessmentAttempt::getAttemptNumber)
                        .max()
                        .orElse(0)
                + 1;
        AssessmentAttempt attempt = new AssessmentAttempt(assessment, studentId, next);
        attempt.attachDocument(stored.id());
        attempt.submit(Instant.now());
        attemptRepository.save(attempt);
        return AttemptResponse.from(
                attempt,
                studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::studentNumber).orElse(null),
                caller.fullName(),
                stored.fileName(),
                stored.sizeBytes());
    }

    public StoredAttemptFile downloadOwn(UUID assessmentId, UUID attemptId) {
        AssessmentAttempt attempt = requireAttempt(attemptId, assessmentId);
        UUID studentId = requireEnrolledStudent(attempt.getAssessment().getCourseSectionId());
        if (!attempt.getStudentId().equals(studentId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        return readFile(attempt);
    }

    public StoredAttemptFile downloadForStaff(UUID assessmentId, UUID attemptId) {
        AssessmentAttempt attempt = requireAttempt(attemptId, assessmentId);
        requireTeacherOrAdmin(attempt.getAssessment().getCourseSectionId());
        return readFile(attempt);
    }

    public record StoredAttemptFile(String fileName, String contentType, byte[] content) {}

    private StoredAttemptFile readFile(AssessmentAttempt attempt) {
        if (attempt.getDocumentId() == null) {
            throw new ResourceNotFoundException(
                    AssessmentErrorCode.ATTEMPT_NOT_FOUND, "No submission file exists for this attempt");
        }
        DocumentStore.StoredFile meta = documentStore
                .find(attempt.getDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ATTEMPT_NOT_FOUND, "No submission file exists for this attempt"));
        byte[] bytes = documentStore
                .content(attempt.getDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ATTEMPT_NOT_FOUND, "No submission file exists for this attempt"));
        return new StoredAttemptFile(meta.fileName(), meta.contentType(), bytes);
    }

    private AttemptResponse toResponse(AssessmentAttempt attempt) {
        StudentDirectory.StudentSummary student = studentDirectory.findById(attempt.getStudentId()).orElse(null);
        String number = student == null ? null : student.studentNumber();
        String name = student == null
                ? null
                : userDirectory.findById(student.userId()).map(UserDirectory.UserSummary::fullName).orElse(null);
        String fileName = null;
        Long size = null;
        if (attempt.getDocumentId() != null) {
            DocumentStore.StoredFile stored = documentStore.find(attempt.getDocumentId()).orElse(null);
            if (stored != null) {
                fileName = stored.fileName();
                size = stored.sizeBytes();
            }
        }
        return AttemptResponse.from(attempt, number, name, fileName, size);
    }

    private AssessmentAttempt requireAttempt(UUID attemptId, UUID assessmentId) {
        AssessmentAttempt attempt = attemptRepository
                .findByIdWithAssessment(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ATTEMPT_NOT_FOUND, "No attempt exists with id " + attemptId));
        if (!attempt.getAssessment().getId().equals(assessmentId)) {
            throw new ResourceNotFoundException(
                    AssessmentErrorCode.ATTEMPT_NOT_FOUND, "No attempt exists with id " + attemptId);
        }
        return attempt;
    }

    private Assessment requireAssessment(UUID assessmentId) {
        return assessmentRepository
                .findById(assessmentId)
                .orElseThrow(() -> notFound(assessmentId));
    }

    private static ResourceNotFoundException notFound(UUID assessmentId) {
        return new ResourceNotFoundException(
                AssessmentErrorCode.ASSESSMENT_NOT_FOUND, "No assessment exists with id " + assessmentId);
    }

    private UUID requireEnrolledStudent(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        requireKnownSection(sectionId);
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        if (!enrollmentDirectory.canAccessLearning(studentId, sectionId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        return studentId;
    }

    private static boolean allowedFile(String contentType, String fileName) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return type.contains("pdf")
                || type.contains("zip")
                || name.endsWith(".pdf")
                || name.endsWith(".zip");
    }

    private void refuseIfExamBlocked(Assessment assessment, UUID studentId) {
        AssessmentType type = assessment.getAssessmentType();
        if (type != AssessmentType.EXAM && type != AssessmentType.QUIZ) {
            return;
        }
        if (examsBlockedForSection(assessment.getCourseSectionId(), studentId)) {
            throw new BusinessException(
                    AssessmentErrorCode.ASSESSMENT_EXAM_BLOCKED,
                    "Exams are blocked until the required tuition installment is paid");
        }
    }

    private boolean examsBlockedForSection(UUID sectionId) {
        UUID studentId = studentDirectory
                .studentIdOfUser(currentUserProvider.require().userId())
                .orElse(null);
        return examsBlockedForSection(sectionId, studentId);
    }

    private boolean examsBlockedForSection(UUID sectionId, UUID studentId) {
        if (studentId == null) {
            return false;
        }
        UUID termId = courseCatalog.findSection(sectionId).map(CourseCatalog.SectionSummary::academicTermId).orElse(null);
        return studentBilling
                .standingOf(studentId, termId, java.time.LocalDate.now(java.time.ZoneOffset.UTC))
                .examBlocked();
    }

    private void requireKnownSection(UUID sectionId) {
        courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ASSESSMENT_SECTION_NOT_FOUND,
                        "No course section exists with id " + sectionId));
    }

    private void requireTeacherOrAdmin(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN)) {
            requireKnownSection(sectionId);
            return;
        }
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ASSESSMENT_SECTION_NOT_FOUND,
                        "No course section exists with id " + sectionId));
        if (caller.hasRole(SecurityRoles.LECTURER) && caller.userId().equals(section.lecturerUserId())) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to change this section");
    }

    private void requireEnrolledOrStaff(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        requireKnownSection(sectionId);
        if (caller.isStaff()) {
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
}
