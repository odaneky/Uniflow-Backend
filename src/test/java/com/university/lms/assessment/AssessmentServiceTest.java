package com.university.lms.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.assessment.domain.Assessment;
import com.university.lms.assessment.domain.AssessmentErrorCode;
import com.university.lms.assessment.domain.AssessmentType;
import com.university.lms.assessment.repository.AssessmentAttemptRepository;
import com.university.lms.assessment.repository.AssessmentRepository;
import com.university.lms.assessment.service.AssessmentOutboxPublisher;
import com.university.lms.assessment.service.AssessmentService;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.finance.api.PaymentStanding;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID SECTION_ID = UUID.randomUUID();

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private AssessmentAttemptRepository attemptRepository;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private EnrollmentDirectory enrollmentDirectory;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private DocumentStore documentStore;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StudentBilling studentBilling;

    @Mock
    private AssessmentOutboxPublisher assessmentOutboxPublisher;

    @Mock
    private StaffAppointments staffAppointments;

    @InjectMocks
    private AssessmentService service;

    @Test
    void refusesAFileUploadAgainstAQuiz() {
        CurrentUser student = new CurrentUser(
                USER_ID,
                "sub",
                "202012345",
                "student@university.test",
                "Demo Student",
                Optional.of("202012345"),
                Set.of(SecurityRoles.STUDENT),
                Set.of());
        when(currentUserProvider.require()).thenReturn(student);
        when(studentDirectory.studentIdOfUser(USER_ID)).thenReturn(Optional.of(STUDENT_ID));
        when(courseCatalog.findSection(SECTION_ID))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        SECTION_ID, UUID.randomUUID(), "COMP2140", "Course", UUID.randomUUID(), "S01", 40, 1, true, null, false)));
        when(enrollmentDirectory.canAccessLearning(STUDENT_ID, SECTION_ID)).thenReturn(true);
        when(studentBilling.standingOf(any(), any(), any())).thenReturn(PaymentStanding.none());

        Assessment quiz = new Assessment(SECTION_ID, "Quiz 1", AssessmentType.QUIZ, BigDecimal.TEN, BigDecimal.TEN);
        quiz.publish();
        when(assessmentRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));

        MockMultipartFile file =
                new MockMultipartFile("file", "answers.pdf", "application/pdf", "pdf".getBytes());

        assertThatThrownBy(() -> service.submitOwn(quiz.getId(), file))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(AssessmentErrorCode.ATTEMPT_NOT_FILE_BASED));
    }
}
