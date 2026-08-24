package com.university.lms.curriculum.service;

import com.university.lms.academic.api.AcademicStructure;
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
import com.university.lms.curriculum.repository.CurriculumVersionRepository;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private final AcademicStructure academicStructure;
    private final CourseCatalog courseCatalog;
    private final AcademicRecord academicRecord;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;

    public CurriculumService(
            ProgrammeRequirementBlockRepository blockRepository,
            CurriculumVersionRepository versionRepository,
            AcademicStructure academicStructure,
            CourseCatalog courseCatalog,
            AcademicRecord academicRecord,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider) {
        this.blockRepository = blockRepository;
        this.versionRepository = versionRepository;
        this.academicStructure = academicStructure;
        this.courseCatalog = courseCatalog;
        this.academicRecord = academicRecord;
        this.studentDirectory = studentDirectory;
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

    @Transactional
    public RequirementBlockResponse addCourse(UUID programmeId, UUID blockId, AddRequirementCourseRequest request) {
        requireCurriculumEditor();
        ProgrammeRequirementBlock block = requireEditableBlock(programmeId, blockId);
        addKnownCourse(block, request.courseId());
        return RequirementBlockResponse.from(block);
    }

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
        Set<UUID> completedCourseIds = new HashSet<>();
        for (Map.Entry<UUID, AcademicRecord.PublishedOverall> entry : latestByCourse.entrySet()) {
            if (entry.getValue().pass()) {
                completedCourseIds.add(entry.getKey());
            }
        }

        List<RequirementProgressResponse> blocks = new ArrayList<>();
        List<CurriculumCourseResponse> remaining = new ArrayList<>();
        Set<UUID> remainingSeen = new HashSet<>();
        List<ProgrammeRequirementBlock> requirementBlocks = findActiveVersion(programme.id())
                .map(version -> blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId()))
                .orElseGet(List::of);
        for (ProgrammeRequirementBlock block : requirementBlocks) {
            int earned = 0;
            List<CurriculumCourseResponse> blockRemaining = new ArrayList<>();
            for (UUID courseId : block.getCourseIds()) {
                CourseCatalog.CourseSummary course = courseCatalog.findCourse(courseId).orElse(null);
                if (course == null) {
                    continue;
                }
                if (completedCourseIds.contains(courseId)) {
                    earned += course.credits();
                    continue;
                }
                CurriculumCourseResponse row =
                        new CurriculumCourseResponse(course.id(), course.courseCode(), course.title(), course.credits());
                blockRemaining.add(row);
                if (remainingSeen.add(courseId)) {
                    remaining.add(row);
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
