package com.university.lms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.dto.TeachingMeetingResponse;
import com.university.lms.course.dto.TeachingSectionResponse;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.enrollment.service.TeachingLoadService;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeachingLoadServiceTest {

    private static final UUID LECTURER_ID = UUID.randomUUID();
    private static final UUID SECTION_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID TERM_ID = UUID.randomUUID();

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private EnrollmentDirectory enrollmentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private TeachingLoadService service;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.require())
                .thenReturn(
                        new CurrentUser(
                                LECTURER_ID,
                                "subject-lecturer",
                                "lecturer",
                                "lecturer@university.test",
                                "Demo Lecturer",
                                Optional.empty(),
                                Set.of(SecurityRoles.LECTURER),
                                Set.of()));
    }

    @Test
    @DisplayName("ownSections includes published meeting times")
    void ownSectionsIncludesMeetings() {
        CourseCatalog.SectionSummary section =
                new CourseCatalog.SectionSummary(
                        SECTION_ID,
                        COURSE_ID,
                        "COMP3101",
                        "Software Engineering",
                        TERM_ID,
                        "01",
                        40,
                        12,
                        true,
                        LECTURER_ID);

        when(courseCatalog.findSectionsTaughtBy(LECTURER_ID)).thenReturn(List.of(section));
        when(enrollmentDirectory.occupyingSeatCount(SECTION_ID)).thenReturn(12);
        when(courseCatalog.findCourse(COURSE_ID))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(COURSE_ID, "COMP3101", "Software Engineering", 3, 3000, true)));
        when(courseCatalog.meetingsOf(SECTION_ID))
                .thenReturn(
                        List.of(new CourseCatalog.Meeting(1, "Mon", "09:00", "10:30", "Room A", "Lecture")));

        List<TeachingSectionResponse> sections = service.ownSections();

        assertThat(sections).hasSize(1);
        TeachingSectionResponse response = sections.getFirst();
        assertThat(response.sectionId()).isEqualTo(SECTION_ID);
        assertThat(response.meetings())
                .containsExactly(new TeachingMeetingResponse(1, "Mon", "09:00", "10:30", "Room A", "Lecture"));
        verify(enrollmentDirectory).occupyingSeatCount(eq(SECTION_ID));
    }
}
