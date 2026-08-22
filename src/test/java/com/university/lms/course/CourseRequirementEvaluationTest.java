package com.university.lms.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.university.lms.course.domain.Course;
import com.university.lms.course.domain.CourseRequirementGroup;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.domain.CourseComponent;
import com.university.lms.course.domain.RequirementKind;
import com.university.lms.course.repository.CourseRepository;
import com.university.lms.course.repository.CourseRequirementGroupRepository;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.course.repository.SectionComponentRepository;
import com.university.lms.course.repository.SectionMeetingRepository;
import com.university.lms.course.service.DefaultCourseCatalog;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseRequirementEvaluationTest {

    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseSectionRepository courseSectionRepository;

    @Mock
    private CourseRequirementGroupRepository requirementGroupRepository;

    @Mock
    private SectionMeetingRepository sectionMeetingRepository;

    @Mock
    private SectionComponentRepository sectionComponentRepository;

    private DefaultCourseCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DefaultCourseCatalog(
                courseRepository,
                courseSectionRepository,
                requirementGroupRepository,
                sectionMeetingRepository,
                sectionComponentRepository);
    }

    @Test
    @DisplayName("an empty group list is always satisfied")
    void noGroupsMeansSatisfied() {
        when(requirementGroupRepository.findByCourseIdOrderByPositionAsc(COURSE_ID)).thenReturn(List.of());

        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(), Set.of(), 0)).isEmpty();
    }

    @Test
    @DisplayName("AND of two prerequisite groups requires both completed courses")
    void andOfPrerequisites() {
        Course cmp2006 = catalogued("CMP2006", "Data Structures", 2);
        Course cmp1005 = catalogued("CMP1005", "Computer Logic", 1);
        when(requirementGroupRepository.findByCourseIdOrderByPositionAsc(COURSE_ID))
                .thenReturn(List.of(
                        prereq(0, cmp2006.getId()),
                        prereq(1, cmp1005.getId())));

        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(cmp2006.getId()), Set.of(), 2))
                .containsExactly("Prerequisite: CMP1005");
        assertThat(catalog.unmetRequirements(
                        COURSE_ID, Set.of(cmp2006.getId(), cmp1005.getId()), Set.of(), 2))
                .isEmpty();
    }

    @Test
    @DisplayName("options inside a group are OR")
    void orInsideAGroup() {
        Course mat1008 = catalogued("MAT1008", "Discrete Mathematics", 1);
        Course mat2003 = catalogued("MAT2003", "Calculus I", 2);
        when(requirementGroupRepository.findByCourseIdOrderByPositionAsc(COURSE_ID))
                .thenReturn(List.of(new CourseRequirementGroup(
                        COURSE_ID,
                        0,
                        RequirementKind.PREREQUISITE,
                        null,
                        Set.of(mat1008.getId(), mat2003.getId()))));

        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(mat1008.getId()), Set.of(), 1)).isEmpty();
        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(mat2003.getId()), Set.of(), 2)).isEmpty();
        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(), Set.of(), 0))
                .anySatisfy(message -> assertThat(message).contains("Prerequisite:").contains("or"));
    }

    @Test
    @DisplayName("a co-requisite may be in progress this term")
    void corequisiteAllowsInProgress() {
        Course cmp2019 = catalogued("CMP2019", "Software Engineering", 2);
        when(requirementGroupRepository.findByCourseIdOrderByPositionAsc(COURSE_ID))
                .thenReturn(List.of(new CourseRequirementGroup(
                        COURSE_ID, 0, RequirementKind.COREQUISITE, null, Set.of(cmp2019.getId()))));

        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(), Set.of(cmp2019.getId()), 0)).isEmpty();
        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(cmp2019.getId()), Set.of(), 2)).isEmpty();
        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(), Set.of(), 0))
                .containsExactly("Co-requisite: CMP2019 (may be taken in the same term)");
    }

    @Test
    @DisplayName("level standing is the highest completed course level")
    void minimumLevelUsesHighestCompleted() {
        when(requirementGroupRepository.findByCourseIdOrderByPositionAsc(COURSE_ID))
                .thenReturn(List.of(new CourseRequirementGroup(
                        COURSE_ID, 0, RequirementKind.MINIMUM_LEVEL, 3, Set.of())));

        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(), Set.of(), 2))
                .containsExactly("Requires Level 3 standing");
        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(), Set.of(), 3)).isEmpty();
        assertThat(catalog.unmetRequirements(COURSE_ID, Set.of(), Set.of(), 4)).isEmpty();
    }

    @Test
    @DisplayName("teaches is true only for the assigned lecturer")
    void teachesIsTheAssignedLecturer() {
        UUID sectionId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        CourseSection section = mock(CourseSection.class);
        when(section.getLecturerUserId()).thenReturn(lecturerId);
        when(courseSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThat(catalog.teaches(lecturerId, sectionId)).isTrue();
        assertThat(catalog.teaches(UUID.randomUUID(), sectionId)).isFalse();
        assertThat(catalog.teaches(null, sectionId)).isFalse();
        assertThat(catalog.teaches(lecturerId, null)).isFalse();
    }

    @Test
    @DisplayName("a lecturer on a component of the set teaches the occurrence")
    void teachesWhenAssignedToAComponent() {
        UUID sectionId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        CourseSection section = mock(CourseSection.class);
        when(section.getLecturerUserId()).thenReturn(null);
        when(courseSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(sectionComponentRepository.existsBySectionIdAndLecturerUserId(sectionId, lecturerId)).thenReturn(true);

        assertThat(catalog.teaches(lecturerId, sectionId)).isTrue();
    }

    @Test
    @DisplayName("an unknown or unassigned section is not taught by anyone")
    void unknownSectionIsNotTaught() {
        UUID sectionId = UUID.randomUUID();
        CourseSection unassigned = mock(CourseSection.class);
        when(unassigned.getLecturerUserId()).thenReturn(null);
        when(courseSectionRepository.findById(sectionId)).thenReturn(Optional.of(unassigned));

        assertThat(catalog.teaches(UUID.randomUUID(), sectionId)).isFalse();
        assertThat(catalog.teaches(UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    private Course catalogued(String code, String title, int level) {
        Course course = new Course(code, title, 3, level, DEPARTMENT_ID, Set.of(CourseComponent.LECTURE));
        lenient().when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        return course;
    }

    private CourseRequirementGroup prereq(int position, UUID optionId) {
        return new CourseRequirementGroup(
                COURSE_ID, position, RequirementKind.PREREQUISITE, null, Set.of(optionId));
    }
}
