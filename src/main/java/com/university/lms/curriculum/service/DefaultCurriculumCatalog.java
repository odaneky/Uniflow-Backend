package com.university.lms.curriculum.service;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.api.CurriculumCatalog;
import com.university.lms.curriculum.domain.CurriculumVersion;
import com.university.lms.curriculum.domain.CurriculumVersionStatus;
import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.domain.RequirementKind;
import com.university.lms.curriculum.repository.CourseSubstitutionRepository;
import com.university.lms.curriculum.repository.CurriculumVersionRepository;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.curriculum.repository.TransferCreditRepository;
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
    private final CourseSubstitutionRepository substitutionRepository;
    private final TransferCreditRepository transferCreditRepository;
    private final AcademicRecord academicRecord;
    private final CourseCatalog courseCatalog;

    public DefaultCurriculumCatalog(
            ProgrammeRequirementBlockRepository blockRepository,
            CurriculumVersionRepository versionRepository,
            CourseSubstitutionRepository substitutionRepository,
            TransferCreditRepository transferCreditRepository,
            AcademicRecord academicRecord,
            CourseCatalog courseCatalog) {
        this.blockRepository = blockRepository;
        this.versionRepository = versionRepository;
        this.substitutionRepository = substitutionRepository;
        this.transferCreditRepository = transferCreditRepository;
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
        if (satisfiedDirectly(studentId, courseId)) {
            return true;
        }
        // An approved substitution satisfies the required course once the substitute is itself
        // satisfied — checked directly only, never chained through another substitution.
        return substitutionRepository
                .findByStudentIdAndRequiredCourseId(studentId, courseId)
                .map(substitution -> satisfiedDirectly(studentId, substitution.getSubstituteCourseId()))
                .orElse(false);
    }

    /**
     * A passing grade or a transfer credit mapped to this course. G2: transfer credit appeared on
     * the transcript but was consulted by neither this check nor degree progress, so a transfer
     * student was blocked from courses they already qualified for.
     */
    private boolean satisfiedDirectly(UUID studentId, UUID courseId) {
        return passedByGrade(studentId, courseId) || hasTransferCredit(studentId, courseId);
    }

    private boolean hasTransferCredit(UUID studentId, UUID courseId) {
        return transferCreditRepository.findByStudentIdOrderByAwardedAtDesc(studentId).stream()
                .anyMatch(transferCredit -> courseId.equals(transferCredit.getInternalCourseId()));
    }

    private boolean passedByGrade(UUID studentId, UUID courseId) {
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
