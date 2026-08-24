package com.university.lms.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.assessment.domain.Assessment;
import com.university.lms.assessment.domain.AssessmentAttempt;
import com.university.lms.assessment.domain.AssessmentErrorCode;
import com.university.lms.assessment.domain.AssessmentType;
import com.university.lms.assessment.domain.QuizOption;
import com.university.lms.assessment.domain.QuizQuestion;
import com.university.lms.assessment.domain.QuizQuestionType;
import com.university.lms.assessment.dto.QuizDtos.CreateQuestionRequest;
import com.university.lms.assessment.dto.QuizDtos.GradeQuestionRequest;
import com.university.lms.assessment.repository.AssessmentAttemptRepository;
import com.university.lms.assessment.repository.AssessmentRepository;
import com.university.lms.assessment.repository.QuizAnswerRepository;
import com.university.lms.assessment.repository.QuizOptionRepository;
import com.university.lms.assessment.repository.QuizQuestionRepository;
import com.university.lms.assessment.service.QuizService;
import com.university.lms.common.exception.ApplicationException;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    private static final UUID LECTURER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STUDENT_USER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID STUDENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SECTION_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID ASSESSMENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private AssessmentAttemptRepository attemptRepository;

    @Mock
    private QuizQuestionRepository questionRepository;

    @Mock
    private QuizOptionRepository optionRepository;

    @Mock
    private QuizAnswerRepository answerRepository;

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

    @InjectMocks
    private QuizService quizService;

    @Test
    void refusesStructuralEditAfterSubmittedAttempt() {
        Assessment quiz = publishedQuiz();
        when(assessmentRepository.findById(ASSESSMENT_ID)).thenReturn(Optional.of(quiz));
        when(currentUserProvider.require()).thenReturn(lecturer());
        when(courseCatalog.findSection(SECTION_ID)).thenReturn(Optional.of(sectionSummary(LECTURER_ID)));
        AssessmentAttempt submitted = new AssessmentAttempt(quiz, STUDENT_ID, 1);
        submitted.submit(Instant.parse("2026-09-01T12:00:00Z"));
        when(attemptRepository.findByAssessmentIdWithAssessment(ASSESSMENT_ID)).thenReturn(List.of(submitted));

        assertThatThrownBy(() -> quizService.addQuestion(
                        ASSESSMENT_ID,
                        new CreateQuestionRequest(
                                "Prompt", QuizQuestionType.SHORT_ANSWER, BigDecimal.ONE, null, true, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(AssessmentErrorCode.QUIZ_STRUCTURE_LOCKED);
    }

    @Test
    void studentAttemptDetailHidesCorrectFlags() {
        Assessment quiz = publishedQuiz();
        when(currentUserProvider.require()).thenReturn(student());
        when(courseCatalog.findSection(SECTION_ID)).thenReturn(Optional.of(sectionSummary(LECTURER_ID)));
        when(studentDirectory.studentIdOfUser(STUDENT_USER)).thenReturn(Optional.of(STUDENT_ID));
        when(enrollmentDirectory.canAccessLearning(STUDENT_ID, SECTION_ID)).thenReturn(true);

        AssessmentAttempt attempt = new AssessmentAttempt(quiz, STUDENT_ID, 1);
        UUID attemptId = attempt.getId();
        when(attemptRepository.findByIdWithAssessment(attemptId)).thenReturn(Optional.of(attempt));

        QuizQuestion question =
                new QuizQuestion(quiz, 1, "Pick one", QuizQuestionType.MULTIPLE_CHOICE, BigDecimal.TEN);
        when(questionRepository.findByAssessmentIdOrderByPositionAsc(ASSESSMENT_ID)).thenReturn(List.of(question));
        when(optionRepository.findByQuestionIdOrderByPositionAsc(question.getId()))
                .thenReturn(List.of(new QuizOption(question, 1, "Right", true), new QuizOption(question, 2, "Wrong", false)));
        when(answerRepository.findByAttemptIdWithQuestion(attemptId)).thenReturn(List.of());
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, STUDENT_USER, "202012345", null, true, ResidencyClassification.IN_DISTRICT)));
        when(userDirectory.findById(STUDENT_USER))
                .thenReturn(Optional.of(new UserDirectory.UserSummary(
                        STUDENT_USER, "202012345", "Sam Student", "student@test", true)));

        var detail = quizService.attemptDetailForStudent(ASSESSMENT_ID, attemptId);
        assertThat(detail.questions()).hasSize(1);
        assertThat(detail.questions().getFirst().options()).allSatisfy(o -> assertThat(o.correct()).isNull());
    }

    @Test
    void onlySectionLecturerMayGrade() {
        Assessment quiz = publishedQuiz();
        when(currentUserProvider.require()).thenReturn(student());
        when(courseCatalog.findSection(SECTION_ID)).thenReturn(Optional.of(sectionSummary(LECTURER_ID)));

        AssessmentAttempt attempt = new AssessmentAttempt(quiz, STUDENT_ID, 1);
        attempt.submit(Instant.parse("2026-09-01T12:00:00Z"));
        when(attemptRepository.findByIdWithAssessment(attempt.getId())).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> quizService.gradeQuestion(
                        ASSESSMENT_ID,
                        attempt.getId(),
                        UUID.randomUUID(),
                        new GradeQuestionRequest(BigDecimal.ONE, "ok")))
                .isInstanceOf(ForbiddenException.class);
    }

    private Assessment publishedQuiz() {
        Assessment quiz = new Assessment(SECTION_ID, "Quiz 1", AssessmentType.QUIZ, BigDecimal.TEN, BigDecimal.TEN);
        quiz.publish();
        try {
            var field = com.university.lms.common.audit.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(quiz, ASSESSMENT_ID);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return quiz;
    }

    private static CurrentUser lecturer() {
        return new CurrentUser(
                LECTURER_ID,
                "sub",
                "lecturer",
                "lecturer@university.test",
                "Lecturer",
                Optional.empty(),
                Set.of(SecurityRoles.LECTURER),
                Set.of());
    }

    private static CurrentUser student() {
        return new CurrentUser(
                STUDENT_USER,
                "sub",
                "202012345",
                "student@university.test",
                "Sam Student",
                Optional.of("202012345"),
                Set.of(SecurityRoles.STUDENT),
                Set.of());
    }

    private static CourseCatalog.SectionSummary sectionSummary(UUID lecturerUserId) {
        return new CourseCatalog.SectionSummary(
                SECTION_ID,
                UUID.randomUUID(),
                "COMP2140", "Course",
                UUID.randomUUID(),
                "UN1",
                40,
                1,
                true,
                lecturerUserId,
                false);
    }
}
