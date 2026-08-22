package com.university.lms.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.domain.RequirementKind;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
                        sectionId, "F", new BigDecimal("0.00"), Instant.parse("2024-01-01T00:00:00Z"))));
        when(courseCatalog.findSection(sectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        sectionId, courseId, "HTM1001", "Intro", UUID.randomUUID(), "A", 30, 10, true, null, false)));
        when(courseCatalog.findCourse(courseId))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(
                        courseId, "HTM1001", "Intro", 3, 1, true)));

        ProgrammeRequirementBlock block =
                new ProgrammeRequirementBlock(programmeId, "Core", RequirementKind.CORE, 3, 0);
        ReflectionTestUtils.setField(block, "id", blockId);
        block.addCourse(courseId);
        when(blockRepository.findByProgrammeIdOrderByPositionAsc(programmeId)).thenReturn(List.of(block));

        DegreeProgressResponse progress = service.progressOf(studentId);

        assertThat(progress.creditsEarned()).isZero();
        assertThat(progress.blocks()).hasSize(1);
        assertThat(progress.blocks().getFirst().creditsEarned()).isZero();
        assertThat(progress.blocks().getFirst().remaining()).hasSize(1);
    }
}
