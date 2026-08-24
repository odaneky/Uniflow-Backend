package com.university.lms.curriculum.service;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.api.CurriculumCatalog;
import com.university.lms.curriculum.domain.CurriculumVersion;
import com.university.lms.curriculum.domain.CurriculumVersionStatus;
import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.domain.RequirementKind;
import com.university.lms.curriculum.repository.CurriculumVersionRepository;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.grading.api.AcademicRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts requirement blocks and the academic record to the published {@link CurriculumCatalog} contract. */
@Service
@Transactional(readOnly = true)
public class DefaultCurriculumCatalog implements CurriculumCatalog {

    private final ProgrammeRequirementBlockRepository blockRepository;
    private final CurriculumVersionRepository versionRepository;
    private final AcademicRecord academicRecord;
    private final CourseCatalog courseCatalog;

    public DefaultCurriculumCatalog(
            ProgrammeRequirementBlockRepository blockRepository,
            CurriculumVersionRepository versionRepository,
            AcademicRecord academicRecord,
            CourseCatalog courseCatalog) {
        this.blockRepository = blockRepository;
        this.versionRepository = versionRepository;
        this.academicRecord = academicRecord;
        this.courseCatalog = courseCatalog;
    }

    @Override
    public boolean allowsEnrolment(UUID programmeId, UUID courseId) {
        if (programmeId == null || courseId == null) {
            return true;
        }
        Optional<CurriculumVersion> activeVersion = resolveActiveVersion(programmeId);
        if (activeVersion.isEmpty()) {
            return true;
        }
        List<ProgrammeRequirementBlock> blocks =
                blockRepository.findByCurriculumVersionIdOrderByPositionAsc(activeVersion.get().getId());
        if (blocks.isEmpty()) {
            return true;
        }
        for (ProgrammeRequirementBlock block : blocks) {
            if (block.getKind() == RequirementKind.FREE_ELECTIVE) {
                return true;
            }
        }
        for (ProgrammeRequirementBlock block : blocks) {
            if (block.getCourseIds().contains(courseId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasPassed(UUID studentId, UUID courseId) {
        if (studentId == null || courseId == null) {
            return false;
        }
        return academicRecord.publishedOverallOf(studentId).stream()
                .filter(overall -> courseIdOf(overall.courseSectionId())
                        .map(courseId::equals)
                        .orElse(false))
                .max(Comparator.comparing(
                        AcademicRecord.PublishedOverall::recordedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(AcademicRecord.PublishedOverall::pass)
                .orElse(false);
    }

    @Override
    public boolean hasPublishedResult(UUID studentId, UUID courseSectionId) {
        if (studentId == null || courseSectionId == null) {
            return false;
        }
        return academicRecord.publishedOverallOf(studentId).stream()
                .anyMatch(overall -> overall.courseSectionId().equals(courseSectionId));
    }

    /** Prefers the published version; falls back to the draft when nothing has been published yet. */
    private Optional<CurriculumVersion> resolveActiveVersion(UUID programmeId) {
        return versionRepository
                .findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.PUBLISHED)
                .or(() -> versionRepository.findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.DRAFT));
    }

    private Optional<UUID> courseIdOf(UUID courseSectionId) {
        return courseCatalog.findSection(courseSectionId).map(CourseCatalog.SectionSummary::courseId);
    }
}
