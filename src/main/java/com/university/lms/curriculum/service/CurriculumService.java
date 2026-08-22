package com.university.lms.curriculum.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.domain.CurriculumErrorCode;
import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.dto.AddRequirementCourseRequest;
import com.university.lms.curriculum.dto.CreateRequirementBlockRequest;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.curriculum.dto.DegreeProgressResponse.CurriculumCourseResponse;
import com.university.lms.curriculum.dto.DegreeProgressResponse.RequirementProgressResponse;
import com.university.lms.curriculum.dto.RequirementBlockResponse;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CurriculumService {

    private final ProgrammeRequirementBlockRepository blockRepository;
    private final AcademicStructure academicStructure;
    private final CourseCatalog courseCatalog;
    private final AcademicRecord academicRecord;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;

    public CurriculumService(
            ProgrammeRequirementBlockRepository blockRepository,
            AcademicStructure academicStructure,
            CourseCatalog courseCatalog,
            AcademicRecord academicRecord,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider) {
        this.blockRepository = blockRepository;
        this.academicStructure = academicStructure;
        this.courseCatalog = courseCatalog;
        this.academicRecord = academicRecord;
        this.studentDirectory = studentDirectory;
        this.currentUserProvider = currentUserProvider;
    }

    public List<RequirementBlockResponse> blocksOf(UUID programmeId) {
        requireProgramme(programmeId);
        return blockRepository.findByProgrammeIdOrderByPositionAsc(programmeId).stream()
                .map(RequirementBlockResponse::from)
                .toList();
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
        if (blockRepository.existsByProgrammeIdAndNameIgnoreCase(programmeId, request.name().trim())) {
            throw new ResourceAlreadyExistsException(
                    CurriculumErrorCode.REQUIREMENT_BLOCK_NAME_EXISTS,
                    "A requirement block named " + request.name() + " already exists on this programme");
        }
        int position = request.position() == null
                ? blockRepository.findByProgrammeIdOrderByPositionAsc(programmeId).size() + 1
                : request.position();
        ProgrammeRequirementBlock block = new ProgrammeRequirementBlock(
                programmeId, request.name().trim(), request.kind(), request.requiredCredits(), position);
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
        ProgrammeRequirementBlock block = requireBlock(programmeId, blockId);
        addKnownCourse(block, request.courseId());
        return RequirementBlockResponse.from(block);
    }

    @Transactional
    public void deleteBlock(UUID programmeId, UUID blockId) {
        requireCurriculumEditor();
        ProgrammeRequirementBlock block = requireBlock(programmeId, blockId);
        blockRepository.delete(block);
    }

    private DegreeProgressResponse progressOf(UUID studentId) {
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
        Set<UUID> completedCourseIds = new HashSet<>();
        for (AcademicRecord.PublishedOverall result : academicRecord.publishedOverallOf(studentId)) {
            courseCatalog
                    .findSection(result.courseSectionId())
                    .ifPresent(section -> completedCourseIds.add(section.courseId()));
        }

        List<RequirementProgressResponse> blocks = new ArrayList<>();
        List<CurriculumCourseResponse> remaining = new ArrayList<>();
        Set<UUID> remainingSeen = new HashSet<>();
        for (ProgrammeRequirementBlock block :
                blockRepository.findByProgrammeIdOrderByPositionAsc(programme.id())) {
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

    private ProgrammeRequirementBlock requireBlock(UUID programmeId, UUID blockId) {
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
        return block;
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
}
