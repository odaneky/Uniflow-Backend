package com.university.lms.communication.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.communication.repository.ConversationParticipantRepository;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.UserDirectory;
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
class MessagingPolicyTest {

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private EnrollmentDirectory enrollmentDirectory;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private ConversationParticipantRepository participantRepository;

    private DefaultMessagingPolicy policy;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID advisorUserId = UUID.randomUUID();
    private final UUID strangerUserId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        policy = new DefaultMessagingPolicy(
                userDirectory, studentDirectory, enrollmentDirectory, courseCatalog, participantRepository);
    }

    @Test
    void studentMayStartConversationWithAssignedAdvisor() {
        CurrentUser student = studentUser();
        when(userDirectory.exists(advisorUserId)).thenReturn(true);
        when(studentDirectory.advisorUserIdOf(studentUserId)).thenReturn(Optional.of(advisorUserId));

        assertThatCode(() -> policy.assertCanStartConversation(student, Set.of(advisorUserId), null))
                .doesNotThrowAnyException();
    }

    @Test
    void studentMayNotMessageArbitraryStaff() {
        CurrentUser student = studentUser();
        when(userDirectory.exists(strangerUserId)).thenReturn(true);
        when(studentDirectory.advisorUserIdOf(studentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policy.assertCanStartConversation(student, Set.of(strangerUserId), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void studentMayMessageSectionLecturerWhenEnrolled() {
        CurrentUser student = studentUser();
        when(userDirectory.exists(strangerUserId)).thenReturn(true);
        when(studentDirectory.advisorUserIdOf(studentUserId)).thenReturn(Optional.empty());
        when(studentDirectory.studentIdOfUser(studentUserId)).thenReturn(Optional.of(studentId));
        when(courseCatalog.teaches(strangerUserId, sectionId)).thenReturn(true);
        when(enrollmentDirectory.canAccessLearning(studentId, sectionId)).thenReturn(true);

        assertThatCode(() -> policy.assertCanStartConversation(student, Set.of(strangerUserId), sectionId))
                .doesNotThrowAnyException();
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
}
