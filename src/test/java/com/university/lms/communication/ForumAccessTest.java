package com.university.lms.communication.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.communication.api.ForumAccess;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.student.api.StudentDirectory;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForumAccessTest {

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private EnrollmentDirectory enrollmentDirectory;

    @Mock
    private CourseCatalog courseCatalog;

    private ForumAccess forumAccess;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID lecturerUserId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        forumAccess = new DefaultForumAccess(studentDirectory, enrollmentDirectory, courseCatalog);
    }

    @Test
    void enrolledStudentMayReadForum() {
        CurrentUser student = studentUser();
        when(studentDirectory.studentIdOfUser(studentUserId)).thenReturn(Optional.of(studentId));
        when(enrollmentDirectory.canAccessLearning(studentId, sectionId)).thenReturn(true);

        assertThatCode(() -> forumAccess.assertCanReadForum(student, sectionId)).doesNotThrowAnyException();
    }

    @Test
    void strangerMayNotReadForum() {
        CurrentUser student = studentUser();
        when(studentDirectory.studentIdOfUser(studentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> forumAccess.assertCanReadForum(student, sectionId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sectionLecturerMayModerateForum() {
        CurrentUser lecturer = lecturerUser();
        when(courseCatalog.teaches(lecturerUserId, sectionId)).thenReturn(true);

        assertThatCode(() -> forumAccess.assertCanModerateForum(lecturer, sectionId))
                .doesNotThrowAnyException();
    }

    @Test
    void studentMayNotModerateForum() {
        CurrentUser student = studentUser();
        when(studentDirectory.studentIdOfUser(studentUserId)).thenReturn(Optional.of(studentId));
        when(enrollmentDirectory.canAccessLearning(studentId, sectionId)).thenReturn(true);

        assertThatThrownBy(() -> forumAccess.assertCanModerateForum(student, sectionId))
                .isInstanceOf(ForbiddenException.class);
    }

    private CurrentUser studentUser() {
        return new CurrentUser(
                studentUserId,
                "sub-student",
                "202012345",
                "student@test.edu",
                "Test Student",
                Optional.of("202012345"),
                Set.of(SecurityRoles.STUDENT),
                Set.of());
    }

    private CurrentUser lecturerUser() {
        return new CurrentUser(
                lecturerUserId,
                "sub-lecturer",
                "lecturer",
                "lecturer@test.edu",
                "Test Lecturer",
                Optional.empty(),
                Set.of(SecurityRoles.LECTURER),
                Set.of());
    }
}
