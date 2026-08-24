package com.university.lms.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.domain.CurriculumVersion;
import com.university.lms.curriculum.domain.CurriculumVersionStatus;
import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.domain.RequirementKind;
import com.university.lms.curriculum.repository.CurriculumVersionRepository;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.curriculum.service.DefaultCurriculumCatalog;
import com.university.lms.grading.api.AcademicRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurriculumCatalogTest {

    private static final UUID PROGRAMME_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

    @Mock
    private ProgrammeRequirementBlockRepository blockRepository;

    @Mock
    private CurriculumVersionRepository versionRepository;

    @Mock
    private AcademicRecord academicRecord;

    @Mock
    private CourseCatalog courseCatalog;

    private DefaultCurriculumCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DefaultCurriculumCatalog(blockRepository, versionRepository, academicRecord, courseCatalog);
    }

    private CurriculumVersion publishedVersion() {
        CurriculumVersion version = new CurriculumVersion(PROGRAMME_ID, "2026/2027", LocalDate.now());
        when(versionRepository.findByProgrammeIdAndStatus(PROGRAMME_ID, CurriculumVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        return version;
    }

    @Test
    @DisplayName("an unpublished curriculum does not block enrolment")
    void emptyBlocksAllowAnyCourse() {
        when(versionRepository.findByProgrammeIdAndStatus(PROGRAMME_ID, CurriculumVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(versionRepository.findByProgrammeIdAndStatus(PROGRAMME_ID, CurriculumVersionStatus.DRAFT))
                .thenReturn(Optional.empty());

        assertThat(catalog.allowsEnrolment(PROGRAMME_ID, COURSE_ID)).isTrue();
    }

    @Test
    @DisplayName("a free-elective block allows any catalog course")
    void freeElectiveAllowsAnyCourse() {
        CurriculumVersion version = publishedVersion();
        ProgrammeRequirementBlock free =
                new ProgrammeRequirementBlock(version, "Free electives", RequirementKind.FREE_ELECTIVE, 6, 2);
        when(blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId())).thenReturn(List.of(free));

        assertThat(catalog.allowsEnrolment(PROGRAMME_ID, COURSE_ID)).isTrue();
    }

    @Test
    @DisplayName("a listed core course is allowed")
    void listedCoreCourseIsAllowed() {
        CurriculumVersion version = publishedVersion();
        ProgrammeRequirementBlock core = new ProgrammeRequirementBlock(version, "Core", RequirementKind.CORE, 60, 0);
        core.addCourse(COURSE_ID);
        when(blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId())).thenReturn(List.of(core));

        assertThat(catalog.allowsEnrolment(PROGRAMME_ID, COURSE_ID)).isTrue();
    }

    @Test
    @DisplayName("a course missing from a published map is refused")
    void unlistedCourseIsRefused() {
        CurriculumVersion version = publishedVersion();
        ProgrammeRequirementBlock core = new ProgrammeRequirementBlock(version, "Core", RequirementKind.CORE, 60, 0);
        core.addCourse(UUID.randomUUID());
        when(blockRepository.findByCurriculumVersionIdOrderByPositionAsc(version.getId())).thenReturn(List.of(core));

        assertThat(catalog.allowsEnrolment(PROGRAMME_ID, COURSE_ID)).isFalse();
    }

    @Test
    @DisplayName("a passing published overall result counts as passed")
    void passingResultCountsAsPassed() {
        UUID studentId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        when(academicRecord.publishedOverallOf(studentId))
                .thenReturn(List.of(new AcademicRecord.PublishedOverall(
                        sectionId, "B", new BigDecimal("3.00"), true, Instant.parse("2026-01-01T00:00:00Z"))));
        when(courseCatalog.findSection(sectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        sectionId, COURSE_ID, "COMP1010", "Intro", UUID.randomUUID(), "A", 30, 10, true, null, false)));

        assertThat(catalog.hasPassed(studentId, COURSE_ID)).isTrue();
    }

    @Test
    @DisplayName("a failed published overall result does not count as passed")
    void failedResultDoesNotCountAsPassed() {
        UUID studentId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        when(academicRecord.publishedOverallOf(studentId))
                .thenReturn(List.of(new AcademicRecord.PublishedOverall(
                        sectionId, "F", BigDecimal.ZERO, false, Instant.parse("2026-01-01T00:00:00Z"))));
        when(courseCatalog.findSection(sectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        sectionId, COURSE_ID, "COMP1010", "Intro", UUID.randomUUID(), "A", 30, 10, true, null, false)));

        assertThat(catalog.hasPassed(studentId, COURSE_ID)).isFalse();
    }

    @Test
    @DisplayName("no published result at all does not count as passed")
    void noResultDoesNotCountAsPassed() {
        UUID studentId = UUID.randomUUID();
        when(academicRecord.publishedOverallOf(studentId)).thenReturn(List.of());

        assertThat(catalog.hasPassed(studentId, COURSE_ID)).isFalse();
    }

    @Test
    @DisplayName("the latest of two attempts decides, not the first")
    void latestAttemptDecidesOverAnEarlierOne() {
        UUID studentId = UUID.randomUUID();
        UUID firstSectionId = UUID.randomUUID();
        UUID retakeSectionId = UUID.randomUUID();
        when(academicRecord.publishedOverallOf(studentId))
                .thenReturn(List.of(
                        new AcademicRecord.PublishedOverall(
                                firstSectionId, "F", BigDecimal.ZERO, false, Instant.parse("2025-01-01T00:00:00Z")),
                        new AcademicRecord.PublishedOverall(
                                retakeSectionId, "B", new BigDecimal("3.00"), true, Instant.parse("2026-01-01T00:00:00Z"))));
        when(courseCatalog.findSection(firstSectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        firstSectionId, COURSE_ID, "COMP1010", "Intro", UUID.randomUUID(), "A", 30, 10, true, null, false)));
        when(courseCatalog.findSection(retakeSectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        retakeSectionId, COURSE_ID, "COMP1010", "Intro", UUID.randomUUID(), "B", 30, 10, true, null, false)));

        assertThat(catalog.hasPassed(studentId, COURSE_ID)).isTrue();
    }
}
