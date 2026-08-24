package com.university.lms.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.domain.CurriculumVersion;
import com.university.lms.curriculum.domain.CurriculumVersionStatus;
import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.domain.RequirementKind;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.curriculum.domain.TransferCredit;
import com.university.lms.curriculum.repository.CourseSubstitutionRepository;
import com.university.lms.curriculum.repository.CurriculumVersionRepository;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.curriculum.repository.TransferCreditRepository;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CurriculumProgressTest {

    @Mock
    private ProgrammeRequirementBlockRepository blockRepository;

    @Mock
    private CurriculumVersionRepository versionRepository;

    @Mock
    private TransferCreditRepository transferCreditRepository;

    @Mock
    private CourseSubstitutionRepository substitutionRepository;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private AcademicRecord academicRecord;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private CurriculumService service;

    @Test
    void failAloneDoesNotSatisfyRequirement() {
        UUID studentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, UUID.randomUUID(), "201945001", programmeId, true)));
        when(academicStructure.findProgramme(programmeId))
                .thenReturn(Optional.of(new AcademicStructure.ProgrammeSummary(
                        programmeId, "BSC-HTM", "Hospitality", "BSc", 120, true, "DEGREE", new BigDecimal("2.00"))));
        when(academicRecord.summaryOf(studentId))
                .thenReturn(new AcademicRecord.Summary(new BigDecimal("0.00"), 3, 0, 1));
        when(academicRecord.publishedOverallOf(studentId))
                .thenReturn(List.of(new AcademicRecord.PublishedOverall(
                        sectionId, "F", new BigDecimal("0.00"), false, Instant.parse("2024-01-01T00:00:00Z"))));
        when(courseCatalog.findSection(sectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        sectionId, courseId, "HTM1001", "Intro", UUID.randomUUID(), "A", 30, 10, true, null, false)));
        when(courseCatalog.findCourse(courseId))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(
                        courseId, "HTM1001", "Intro", 3, 1, true)));

        CurriculumVersion version = new CurriculumVersion(programmeId, "2026/2027", LocalDate.now());
        when(versionRepository.findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));

        ProgrammeRequirementBlock block = new ProgrammeRequirementBlock(version, "Core", RequirementKind.CORE, 3, 0);
        ReflectionTestUtils.setField(block, "id", blockId);
        block.addCourse(courseId);
        when(blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId()))
                .thenReturn(List.of(block));

        DegreeProgressResponse progress = service.progressOf(studentId);

        assertThat(progress.creditsEarned()).isZero();
        assertThat(progress.blocks()).hasSize(1);
        assertThat(progress.blocks().getFirst().creditsEarned()).isZero();
        assertThat(progress.blocks().getFirst().remaining()).hasSize(1);
    }

    @Test
    @DisplayName("a transfer credit satisfies the course it is mapped to, the same as a passing grade")
    void transferCreditSatisfiesItsMappedCourse() {
        UUID studentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, UUID.randomUUID(), "201945001", programmeId, true)));
        when(academicStructure.findProgramme(programmeId))
                .thenReturn(Optional.of(new AcademicStructure.ProgrammeSummary(
                        programmeId, "BSC-HTM", "Hospitality", "BSc", 120, true, "DEGREE", new BigDecimal("2.00"))));
        when(academicRecord.summaryOf(studentId)).thenReturn(new AcademicRecord.Summary(null, 0, 0, 0));
        when(academicRecord.publishedOverallOf(studentId)).thenReturn(List.of());
        when(courseCatalog.findCourse(courseId))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(courseId, "HTM1001", "Intro", 3, 1, true)));
        when(transferCreditRepository.findByStudentIdOrderByAwardedAtDesc(studentId))
                .thenReturn(List.of(new TransferCredit(
                        studentId, "Prior College", "INTRO-101", "Intro", courseId, 3, LocalDate.of(2024, 1, 1), null)));

        CurriculumVersion version = new CurriculumVersion(programmeId, "2026/2027", LocalDate.now());
        when(versionRepository.findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));

        ProgrammeRequirementBlock block = new ProgrammeRequirementBlock(version, "Core", RequirementKind.CORE, 3, 0);
        ReflectionTestUtils.setField(block, "id", blockId);
        block.addCourse(courseId);
        when(blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId()))
                .thenReturn(List.of(block));

        DegreeProgressResponse progress = service.progressOf(studentId);

        assertThat(progress.blocks().getFirst().creditsEarned()).isEqualTo(3);
        assertThat(progress.blocks().getFirst().remaining()).isEmpty();
    }

    @Test
    @DisplayName("a block naming no courses is satisfied by spare credit, not permanently unsatisfiable")
    void emptyBlockIsSatisfiedBySpareCredit() {
        UUID studentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, UUID.randomUUID(), "201945001", programmeId, true)));
        when(academicStructure.findProgramme(programmeId))
                .thenReturn(Optional.of(new AcademicStructure.ProgrammeSummary(
                        programmeId, "BSC-HTM", "Hospitality", "BSc", 120, true, "DEGREE", new BigDecimal("2.00"))));
        when(academicRecord.summaryOf(studentId)).thenReturn(new AcademicRecord.Summary(null, 0, 0, 0));
        when(academicRecord.publishedOverallOf(studentId)).thenReturn(List.of());
        // No internal course mapping — general transfer credit with nothing named to satisfy it.
        when(transferCreditRepository.findByStudentIdOrderByAwardedAtDesc(studentId))
                .thenReturn(List.of(
                        new TransferCredit(studentId, "Prior College", "ELEC-100", "Elective", null, 6, LocalDate.of(2024, 1, 1), null)));

        CurriculumVersion version = new CurriculumVersion(programmeId, "2026/2027", LocalDate.now());
        when(versionRepository.findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));

        ProgrammeRequirementBlock freeElective =
                new ProgrammeRequirementBlock(version, "Free electives", RequirementKind.FREE_ELECTIVE, 6, 0);
        ReflectionTestUtils.setField(freeElective, "id", blockId);
        when(blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId()))
                .thenReturn(List.of(freeElective));

        DegreeProgressResponse progress = service.progressOf(studentId);

        assertThat(progress.blocks().getFirst().creditsEarned()).isEqualTo(6);
    }

    @Test
    @DisplayName("an approved substitution satisfies the required course once the substitute is passed")
    void approvedSubstitutionSatisfiesTheRequiredCourse() {
        UUID studentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();
        UUID requiredCourseId = UUID.randomUUID();
        UUID substituteCourseId = UUID.randomUUID();
        UUID substituteSectionId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, UUID.randomUUID(), "201945001", programmeId, true)));
        when(academicStructure.findProgramme(programmeId))
                .thenReturn(Optional.of(new AcademicStructure.ProgrammeSummary(
                        programmeId, "BSC-HTM", "Hospitality", "BSc", 120, true, "DEGREE", new BigDecimal("2.00"))));
        when(academicRecord.summaryOf(studentId))
                .thenReturn(new AcademicRecord.Summary(new BigDecimal("3.50"), 3, 3, 1));
        when(academicRecord.publishedOverallOf(studentId))
                .thenReturn(List.of(new AcademicRecord.PublishedOverall(
                        substituteSectionId, "A", new BigDecimal("4.00"), true, Instant.parse("2025-01-01T00:00:00Z"))));
        when(courseCatalog.findSection(substituteSectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        substituteSectionId, substituteCourseId, "HTM2020", "Advanced Topic", UUID.randomUUID(), "A", 30, 10, true, null, false)));
        when(courseCatalog.findCourse(requiredCourseId))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(requiredCourseId, "HTM1010", "Foundations", 3, 1, true)));
        // Satisfied but not named in any block — the code correctly offers it to the spare-credit
        // pool for other (empty) blocks, so its credit value is looked up too.
        when(courseCatalog.findCourse(substituteCourseId))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(substituteCourseId, "HTM2020", "Advanced Topic", 3, 2, true)));
        when(substitutionRepository.findByStudentId(studentId))
                .thenReturn(List.of(new com.university.lms.curriculum.domain.CourseSubstitution(
                        studentId, requiredCourseId, substituteCourseId, UUID.randomUUID(), UUID.randomUUID())));

        CurriculumVersion version = new CurriculumVersion(programmeId, "2026/2027", LocalDate.now());
        when(versionRepository.findByProgrammeIdAndStatus(programmeId, CurriculumVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));

        ProgrammeRequirementBlock block = new ProgrammeRequirementBlock(version, "Core", RequirementKind.CORE, 3, 0);
        ReflectionTestUtils.setField(block, "id", blockId);
        block.addCourse(requiredCourseId);
        when(blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId()))
                .thenReturn(List.of(block));

        DegreeProgressResponse progress = service.progressOf(studentId);

        assertThat(progress.blocks().getFirst().creditsEarned()).isEqualTo(3);
        assertThat(progress.blocks().getFirst().remaining()).isEmpty();
    }
}
