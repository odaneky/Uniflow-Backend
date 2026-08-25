package com.university.lms.curriculum.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.domain.CurriculumErrorCode;
import com.university.lms.curriculum.domain.CurriculumVersion;
import com.university.lms.curriculum.domain.CurriculumVersionStatus;
import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.dto.AddRequirementCourseRequest;
import com.university.lms.curriculum.dto.CreateRequirementBlockRequest;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.curriculum.dto.DegreeProgressResponse.CurriculumCourseResponse;
import com.university.lms.curriculum.dto.DegreeProgressResponse.RequirementProgressResponse;
import com.university.lms.curriculum.dto.RequirementBlockResponse;
import com.university.lms.curriculum.domain.TransferCredit;
import com.university.lms.curriculum.repository.CourseSubstitutionRepository;
import com.university.lms.curriculum.repository.CurriculumVersionRepository;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.curriculum.repository.TransferCreditRepository;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import com.university.lms.student.api.StudentProgrammeEnrolments;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CurriculumService {

    private final ProgrammeRequirementBlockRepository blockRepository;
    private final CurriculumVersionRepository versionRepository;
    private final TransferCreditRepository transferCreditRepository;
    private final CourseSubstitutionRepository substitutionRepository;
    private final AcademicStructure academicStructure;
    private final CourseCatalog courseCatalog;
    private final AcademicRecord academicRecord;
    private final StudentDirectory studentDirectory;
    private final StudentProgrammeEnrolments studentProgrammeEnrolments;
    private final CurrentUserProvider currentUserProvider;

    public CurriculumService(
            ProgrammeRequirementBlockRepository blockRepository,
            CurriculumVersionRepository versionRepository,
            TransferCreditRepository transferCreditRepository,
            CourseSubstitutionRepository substitutionRepository,
            AcademicStructure academicStructure,
            CourseCatalog courseCatalog,
            AcademicRecord academicRecord,
            StudentDirectory studentDirectory,
            StudentProgrammeEnrolments studentProgrammeEnrolments,
            CurrentUserProvider currentUserProvider) {
        this.blockRepository = blockRepository;
        this.versionRepository = versionRepository;
        this.transferCreditRepository = transferCreditRepository;
        this.substitutionRepository = substitutionRepository;
        this.academicStructure = academicStructure;
        this.courseCatalog = courseCatalog;
        this.academicRecord = academicRecord;
        this.studentDirectory = studentDirectory;
        this.studentProgrammeEnrolments = studentProgrammeEnrolments;
        this.currentUserProvider = currentUserProvider;
    }

    public List<RequirementBlockResponse> blocksOf(UUID programmeId) {
        requireProgramme(programmeId);
        return findActiveVersion(programmeId)
                .map(version -> blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId()).stream()
                        .map(RequirementBlockResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    public DegreeProgressResponse ownProgress() {
        CurrentUser caller = currentUserProvider.require();
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        return progressOf(studentId);
    }

    @Auditable(
            action = AuditTrail.Action.REQUIREMENT_BLOCK_CREATED,
            entityType = AuditTrail.EntityType.REQUIREMENT_BLOCK,
            entityId = "#result.id()",
            details = "#result.name()")
    @Transactional
    public RequirementBlockResponse createBlock(UUID programmeId, CreateRequirementBlockRequest request) {
        requireCurriculumEditor();
        requireProgramme(programmeId);
        CurriculumVersion version = resolveEditableVersion(programmeId);
        if (blockRepository.existsByCurriculumVersionIdAndNameIgnoreCase(version.getId(), request.name().trim())) {
            throw new ResourceAlreadyExistsException(
                    CurriculumErrorCode.REQUIREMENT_BLOCK_NAME_EXISTS,
                    "A requirement block named " + request.name() + " already exists on this programme");
        }
        int position = request.position() == null
                ? blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId()).size() + 1
                : request.position();
        ProgrammeRequirementBlock block = new ProgrammeRequirementBlock(
                version, request.name().trim(), request.kind(), request.requiredCredits(), position);
        if (request.courseIds() != null) {
            for (UUID courseId : request.courseIds()) {
                addKnownCourse(block, courseId);
            }
        }
        try {
            return RequirementBlockResponse.from(blockRepository.saveAndFlush(block));
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    CurriculumErrorCode.REQUIREMENT_BLOCK_NAME_EXISTS,
                    "A requirement block named " + request.name() + " already exists on this programme",
                    ex);
        }
    }

    @Auditable(
            action = AuditTrail.Action.REQUIREMENT_BLOCK_COURSE_ADDED,
            entityType = AuditTrail.EntityType.REQUIREMENT_BLOCK,
            entityId = "#blockId")
    @Transactional
    public RequirementBlockResponse addCourse(UUID programmeId, UUID blockId, AddRequirementCourseRequest request) {
        requireCurriculumEditor();
        ProgrammeRequirementBlock block = requireEditableBlock(programmeId, blockId);
        addKnownCourse(block, request.courseId());
        return RequirementBlockResponse.from(block);
    }

    @Auditable(
            action = AuditTrail.Action.REQUIREMENT_BLOCK_DELETED,
            entityType = AuditTrail.EntityType.REQUIREMENT_BLOCK,
            entityId = "#blockId")
    @Transactional
    public void deleteBlock(UUID programmeId, UUID blockId) {
        requireCurriculumEditor();
        ProgrammeRequirementBlock block = requireEditableBlock(programmeId, blockId);
        blockRepository.delete(block);
    }

    /**
     * Publishes the programme's current draft curriculum version, retiring whichever version was
     * previously published. From this moment its requirement blocks are immutable — see
     * {@link CurriculumVersion#isEditable()}.
     */
    @Auditable(
            action = AuditTrail.Action.CURRICULUM_VERSION_PUBLISHED,
            entityType = AuditTrail.EntityType.PROGRAMME,
            entityId = "#programmeId")
    @Transactional
    public void publishVersion(UUID programmeId) {
        requireRegistry();
        requireProgramme(programmeId);
        CurriculumVersion draft = versionRepository
                .findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CurriculumErrorCode.CURRICULUM_VERSION_NOT_FOUND,
                        "No draft curriculum version exists for programme " + programmeId));
        versionRepository
                .findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.PUBLISHED)
                .ifPresent(CurriculumVersion::retire);
        draft.publish();
    }

    DegreeProgressResponse progressOf(UUID studentId) {
        StudentDirectory.StudentSummary student = studentDirectory
                .findById(studentId)
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        AcademicStructure.ProgrammeSummary programme = academicStructure
                .findProgramme(student.programmeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CurriculumErrorCode.PROGRAMME_NOT_FOUND,
                        "No programme exists with id " + student.programmeId()));
        AcademicRecord.Summary summary = academicRecord.summaryOf(studentId);
        // Most recent published overall per course; completed only when that sit is PASS.
        Map<UUID, AcademicRecord.PublishedOverall> latestByCourse = new HashMap<>();
        List<AcademicRecord.PublishedOverall> chronological = academicRecord.publishedOverallOf(studentId).stream()
                .sorted(Comparator.comparing(
                        AcademicRecord.PublishedOverall::recordedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        for (AcademicRecord.PublishedOverall result : chronological) {
            courseCatalog.findSection(result.courseSectionId()).ifPresent(section -> {
                latestByCourse.put(section.courseId(), result);
            });
        }
        Set<UUID> passedByGrade = new HashSet<>();
        for (Map.Entry<UUID, AcademicRecord.PublishedOverall> entry : latestByCourse.entrySet()) {
            if (entry.getValue().pass()) {
                passedByGrade.add(entry.getKey());
            }
        }

        // Transfer credit satisfies a block the same way a passing grade does — a course mapped to
        // an internal course id counts as complete; one with none is general credit, spent below on
        // whatever block still has room for it. G2: transfer credits were on the transcript but
        // consulted by neither this check nor the prerequisite check, so a transfer student was
        // blocked from courses and requirements they already qualified for.
        Map<UUID, Integer> transferCreditsByCourse = new LinkedHashMap<>();
        int unmappedTransferCredits = 0;
        for (TransferCredit transferCredit : transferCreditRepository.findByStudentIdOrderByAwardedAtDesc(studentId)) {
            if (transferCredit.getInternalCourseId() != null) {
                transferCreditsByCourse.putIfAbsent(transferCredit.getInternalCourseId(), transferCredit.getCreditsAwarded());
            } else {
                unmappedTransferCredits += transferCredit.getCreditsAwarded();
            }
        }
        Set<UUID> satisfiedCourseIds = new HashSet<>(passedByGrade);
        satisfiedCourseIds.addAll(transferCreditsByCourse.keySet());

        // An approved substitution satisfies the required course once the substitute is itself
        // satisfied — checked against grades and transfer credit only, never chained through
        // another substitution. D2: the request workflow used to validate a substitution's payload
        // and record nothing, so an approved substitution never actually excused the requirement.
        for (var substitution : substitutionRepository.findByStudentId(studentId)) {
            if (satisfiedCourseIds.contains(substitution.getSubstituteCourseId())) {
                satisfiedCourseIds.add(substitution.getRequiredCourseId());
            }
        }

        List<ProgrammeRequirementBlock> requirementBlocks = resolveVersionFor(
                        studentId, programme.id(), student.curriculumVersionId())
                .map(version -> blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId()))
                .orElseGet(List::of);

        // A block naming no courses (a FREE_ELECTIVE or GENERAL_EDUCATION pool) used to be
        // unsatisfiable no matter how many credits the student earned, since the loop below had
        // nothing to iterate. It draws instead from a spare-credit pool: unmapped transfer credit,
        // plus any satisfied course not claimed by a block that names courses explicitly.
        Set<UUID> namedCourseIds = new HashSet<>();
        for (ProgrammeRequirementBlock block : requirementBlocks) {
            namedCourseIds.addAll(block.getCourseIds());
        }
        int sparePool = unmappedTransferCredits;
        for (UUID courseId : satisfiedCourseIds) {
            if (!namedCourseIds.contains(courseId)) {
                sparePool += creditsOf(courseId, transferCreditsByCourse);
            }
        }

        List<RequirementProgressResponse> blocks = new ArrayList<>();
        List<CurriculumCourseResponse> remaining = new ArrayList<>();
        Set<UUID> remainingSeen = new HashSet<>();
        for (ProgrammeRequirementBlock block : requirementBlocks) {
            int earned;
            List<CurriculumCourseResponse> blockRemaining = new ArrayList<>();
            if (block.getCourseIds().isEmpty()) {
                int allocated = Math.min(sparePool, block.getRequiredCredits());
                sparePool -= allocated;
                earned = allocated;
            } else {
                earned = 0;
                for (UUID courseId : block.getCourseIds()) {
                    CourseCatalog.CourseSummary course = courseCatalog.findCourse(courseId).orElse(null);
                    if (course == null) {
                        continue;
                    }
                    if (satisfiedCourseIds.contains(courseId)) {
                        earned += course.credits();
                        continue;
                    }
                    CurriculumCourseResponse row = new CurriculumCourseResponse(
                            course.id(), course.courseCode(), course.title(), course.credits());
                    blockRemaining.add(row);
                    if (remainingSeen.add(courseId)) {
                        remaining.add(row);
                    }
                }
            }
            blocks.add(new RequirementProgressResponse(
                    block.getId(),
                    block.getName(),
                    block.getKind(),
                    block.getRequiredCredits(),
                    Math.min(earned, block.getRequiredCredits()),
                    blockRemaining));
        }

        return new DegreeProgressResponse(
                programme.id(),
                programme.code(),
                programme.name(),
                programme.degreeAward(),
                programme.totalCredits(),
                summary.creditsEarned(),
                summary.creditsAttempted(),
                summary.gpa(),
                blocks,
                remaining);
    }

    private int creditsOf(UUID courseId, Map<UUID, Integer> transferCreditsByCourse) {
        return courseCatalog
                .findCourse(courseId)
                .map(CourseCatalog.CourseSummary::credits)
                .orElseGet(() -> transferCreditsByCourse.getOrDefault(courseId, 0));
    }

    /**
     * The student's resolved curriculum version's residency requirement — the minimum credits
     * {@link DegreeProgressResponse#creditsEarned()} must reach without counting transfer credit — if
     * one is configured. Empty when the student has no resolvable version, or that version has none
     * set.
     */
    Optional<Integer> residencyCreditsFor(UUID studentId) {
        return studentDirectory
                .findById(studentId)
                .flatMap(student -> resolveVersionFor(studentId, student.programmeId(), student.curriculumVersionId()))
                .map(CurriculumVersion::getResidencyCredits);
    }

    private void addKnownCourse(ProgrammeRequirementBlock block, UUID courseId) {
        if (!courseCatalog.courseExists(courseId)) {
            throw new ResourceNotFoundException(
                    CurriculumErrorCode.REQUIREMENT_COURSE_UNKNOWN, "No course exists with id " + courseId);
        }
        if (block.getCourseIds().contains(courseId)) {
            throw new ResourceAlreadyExistsException(
                    CurriculumErrorCode.REQUIREMENT_COURSE_ALREADY_LISTED,
                    "That course is already listed on this requirement block");
        }
        block.addCourse(courseId);
    }

    /** Finds the block and refuses it if its curriculum version is no longer editable. */
    private ProgrammeRequirementBlock requireEditableBlock(UUID programmeId, UUID blockId) {
        requireProgramme(programmeId);
        ProgrammeRequirementBlock block = blockRepository
                .findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CurriculumErrorCode.REQUIREMENT_BLOCK_NOT_FOUND,
                        "No requirement block exists with id " + blockId));
        if (!block.getProgrammeId().equals(programmeId)) {
            throw new ResourceNotFoundException(
                    CurriculumErrorCode.REQUIREMENT_BLOCK_NOT_FOUND,
                    "No requirement block exists with id " + blockId);
        }
        if (!block.getCurriculumVersion().isEditable()) {
            throw new BusinessException(
                    CurriculumErrorCode.CURRICULUM_VERSION_NOT_EDITABLE,
                    "This requirement block's curriculum version (" + block.getCurriculumVersion().getCatalogYear()
                            + ") is published and can no longer be changed");
        }
        return block;
    }

    /**
     * The curriculum version a student's degree audit is resolved against.
     *
     * <p>A student already bound to a version (via {@link StudentProgrammeEnrolments#bindCurriculumVersion})
     * always resolves to that exact version, published or not — this is what keeps a past audit's
     * answer from moving when the programme's requirements are later revised. An unbound student
     * resolves against the programme's current active version, same as before; if that version is
     * {@code PUBLISHED} (not a still-editable {@code DRAFT}), this also binds it, so the very next
     * time this student's progress is resolved, it is pinned to today's answer rather than whatever
     * is active by then. A student who is never read before a version publishes is bound to whichever
     * version happens to be active on their first read — the same fail-open default the unbound case
     * already had, just made permanent instead of moving underneath them on every subsequent call.
     */
    private Optional<CurriculumVersion> resolveVersionFor(UUID studentId, UUID programmeId, UUID boundVersionId) {
        if (boundVersionId != null) {
            return versionRepository.findById(boundVersionId);
        }
        Optional<CurriculumVersion> active = findActiveVersion(programmeId);
        active.filter(version -> version.getStatus() == CurriculumVersionStatus.PUBLISHED)
                .ifPresent(version -> studentProgrammeEnrolments.bindCurriculumVersion(studentId, version.getId()));
        return active;
    }

    /** Prefers the published version; falls back to the draft when nothing has been published yet. */
    private Optional<CurriculumVersion> findActiveVersion(UUID programmeId) {
        return versionRepository
                .findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.PUBLISHED)
                .or(() -> versionRepository.findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.DRAFT));
    }

    /** The programme's current draft, creating one if none exists yet — writes always land in a draft. */
    private CurriculumVersion resolveEditableVersion(UUID programmeId) {
        return versionRepository
                .findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.DRAFT)
                .orElseGet(() -> versionRepository.save(
                        new CurriculumVersion(programmeId, defaultCatalogYear(programmeId), LocalDate.now())));
    }

    /**
     * A generated placeholder label for an auto-created draft — a registrar setting up a real
     * catalog year is expected to rename it. Offset by how many versions this programme already
     * has, not just today's date: two drafts auto-created for the same programme on the same day
     * (the second, after the first was published) must not collide on {@code uk_curriculum_versions}.
     */
    private String defaultCatalogYear(UUID programmeId) {
        int year = LocalDate.now().getYear() + (int) versionRepository.countByProgrammeId(programmeId);
        return year + "/" + (year + 1);
    }

    private void requireProgramme(UUID programmeId) {
        if (!academicStructure.programmeExists(programmeId)) {
            throw new ResourceNotFoundException(
                    CurriculumErrorCode.PROGRAMME_NOT_FOUND, "No programme exists with id " + programmeId);
        }
    }

    private void requireCurriculumEditor() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to change this record");
        }
    }

    /** Publishing freezes a curriculum version forever — narrower than the general editor scope. */
    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to publish a curriculum version");
        }
    }
}
