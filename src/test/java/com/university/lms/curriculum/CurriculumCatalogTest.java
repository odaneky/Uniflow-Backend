package com.university.lms.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.domain.RequirementKind;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import com.university.lms.curriculum.service.DefaultCurriculumCatalog;
import java.util.List;
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

    private DefaultCurriculumCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DefaultCurriculumCatalog(blockRepository);
    }

    @Test
    @DisplayName("an unpublished curriculum does not block enrolment")
    void emptyBlocksAllowAnyCourse() {
        when(blockRepository.findByProgrammeIdOrderByPositionAsc(PROGRAMME_ID)).thenReturn(List.of());

        assertThat(catalog.allowsEnrolment(PROGRAMME_ID, COURSE_ID)).isTrue();
    }

    @Test
    @DisplayName("a free-elective block allows any catalog course")
    void freeElectiveAllowsAnyCourse() {
        ProgrammeRequirementBlock free =
                new ProgrammeRequirementBlock(PROGRAMME_ID, "Free electives", RequirementKind.FREE_ELECTIVE, 6, 2);
        when(blockRepository.findByProgrammeIdOrderByPositionAsc(PROGRAMME_ID)).thenReturn(List.of(free));

        assertThat(catalog.allowsEnrolment(PROGRAMME_ID, COURSE_ID)).isTrue();
    }

    @Test
    @DisplayName("a listed core course is allowed")
    void listedCoreCourseIsAllowed() {
        ProgrammeRequirementBlock core =
                new ProgrammeRequirementBlock(PROGRAMME_ID, "Core", RequirementKind.CORE, 60, 0);
        core.addCourse(COURSE_ID);
        when(blockRepository.findByProgrammeIdOrderByPositionAsc(PROGRAMME_ID)).thenReturn(List.of(core));

        assertThat(catalog.allowsEnrolment(PROGRAMME_ID, COURSE_ID)).isTrue();
    }

    @Test
    @DisplayName("a course missing from a published map is refused")
    void unlistedCourseIsRefused() {
        ProgrammeRequirementBlock core =
                new ProgrammeRequirementBlock(PROGRAMME_ID, "Core", RequirementKind.CORE, 60, 0);
        core.addCourse(UUID.randomUUID());
        when(blockRepository.findByProgrammeIdOrderByPositionAsc(PROGRAMME_ID)).thenReturn(List.of(core));

        assertThat(catalog.allowsEnrolment(PROGRAMME_ID, COURSE_ID)).isFalse();
    }
}
