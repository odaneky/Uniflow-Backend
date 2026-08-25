package com.university.lms.request.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.grading.api.GradeDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.api.StudentDirectory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceRequestWorkflowTest {

    @Mock
    StudentDirectory studentDirectory;

    @Mock
    CourseCatalog courseCatalog;

    @Mock
    GradeDirectory gradeDirectory;

    ServiceRequestWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new ServiceRequestWorkflow(studentDirectory, courseCatalog, gradeDirectory);
    }

    @Test
    void allowsSubmittedToInReview() {
        ServiceRequest request = new ServiceRequest(
                UUID.randomUUID(), ServiceRequestType.TRANSCRIPT, "TR-00001", null, "{}", null);
        assertThatCode(() -> workflow.assertTransition(request, ServiceRequestStatus.IN_REVIEW))
                .doesNotThrowAnyException();
    }

    @Test
    void advisorMayReviewWithdrawalForAdvisee() {
        UUID advisorId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ServiceRequest request = new ServiceRequest(
                studentId, ServiceRequestType.WITHDRAWAL, "WD-00001", null, "{}", advisorId);
        request.transitionTo(ServiceRequestStatus.SUBMITTED, null, null, java.time.Instant.now());

        CurrentUser advisor = new CurrentUser(
                advisorId,
                "sub-adv",
                "adv",
                "adv@test.edu",
                "Advisor",
                Optional.empty(),
                Set.of("ACADEMIC_ADVISOR"),
                Set.of());
        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, studentUserId, "202012345", UUID.randomUUID(), true, ResidencyClassification.IN_DISTRICT)));
        when(studentDirectory.adviseeUserIdsOf(advisorId)).thenReturn(List.of(studentUserId));

        assertThatCode(() -> workflow.assertStaffAction(advisor, request, ServiceRequestStatus.IN_REVIEW))
                .doesNotThrowAnyException();
    }

    @Test
    void financialAidOfficerMayReviewAndCompleteSapAppeal() {
        ServiceRequest request = new ServiceRequest(
                UUID.randomUUID(), ServiceRequestType.SAP_APPEAL, "SA-00001", null, "{}", null);
        CurrentUser officer = new CurrentUser(
                UUID.randomUUID(),
                "sub-fao",
                "fao",
                "fao@test.edu",
                "Financial Aid Officer",
                Optional.empty(),
                Set.of("FINANCIAL_AID_OFFICER"),
                Set.of());

        assertThatCode(() -> workflow.assertStaffAction(officer, request, ServiceRequestStatus.IN_REVIEW))
                .doesNotThrowAnyException();
        assertThatCode(() -> workflow.assertStaffAction(officer, request, ServiceRequestStatus.COMPLETED))
                .doesNotThrowAnyException();
    }

    @Test
    void financialAidOfficerCannotReviewOtherRequestTypes() {
        ServiceRequest request = new ServiceRequest(
                UUID.randomUUID(), ServiceRequestType.TRANSCRIPT, "TR-00001", null, "{}", null);
        CurrentUser officer = new CurrentUser(
                UUID.randomUUID(),
                "sub-fao",
                "fao",
                "fao@test.edu",
                "Financial Aid Officer",
                Optional.empty(),
                Set.of("FINANCIAL_AID_OFFICER"),
                Set.of());

        assertThatThrownBy(() -> workflow.assertStaffAction(officer, request, ServiceRequestStatus.IN_REVIEW))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void registrarStillReviewsAndCompletesSapAppealUnconditionally() {
        ServiceRequest request = new ServiceRequest(
                UUID.randomUUID(), ServiceRequestType.SAP_APPEAL, "SA-00002", null, "{}", null);
        CurrentUser registrar = new CurrentUser(
                UUID.randomUUID(),
                "sub-reg",
                "reg",
                "reg@test.edu",
                "Registrar",
                Optional.empty(),
                Set.of("REGISTRAR"),
                Set.of());

        assertThatCode(() -> workflow.assertStaffAction(registrar, request, ServiceRequestStatus.IN_REVIEW))
                .doesNotThrowAnyException();
        assertThatCode(() -> workflow.assertStaffAction(registrar, request, ServiceRequestStatus.COMPLETED))
                .doesNotThrowAnyException();
    }
}
