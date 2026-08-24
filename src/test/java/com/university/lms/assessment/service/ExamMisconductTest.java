package com.university.lms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.assessment.domain.ExamMisconductRecord;
import com.university.lms.assessment.domain.ExamSitting;
import com.university.lms.assessment.dto.ExamMisconductRecordResponse;
import com.university.lms.assessment.dto.ReportExamMisconductRequest;
import com.university.lms.assessment.repository.ExamMisconductRecordRepository;
import com.university.lms.assessment.repository.ExamSittingRepository;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.api.Notifier;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
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
class ExamMisconductTest {

    @Mock
    ExamSittingRepository examSittingRepository;

    @Mock
    ExamMisconductRecordRepository examMisconductRecordRepository;

    @Mock
    CourseCatalog courseCatalog;

    @Mock
    EnrollmentDirectory enrollmentDirectory;

    @Mock
    StudentDirectory studentDirectory;

    @Mock
    UserDirectory userDirectory;

    @Mock
    Notifier notifier;

    @Mock
    AuditTrail auditTrail;

    @Mock
    CurrentUserProvider currentUserProvider;

    ExamScheduleService service;

    UUID sittingId;
    UUID studentId;
    UUID staffUserId;

    @BeforeEach
    void setUp() {
        service = new ExamScheduleService(
                examSittingRepository,
                examMisconductRecordRepository,
                courseCatalog,
                enrollmentDirectory,
                studentDirectory,
                userDirectory,
                notifier,
                auditTrail,
                currentUserProvider);

        sittingId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        staffUserId = UUID.randomUUID();

        ExamSitting sitting =
                new ExamSitting(UUID.randomUUID(), "Midterm", Instant.now(), 90, "Hall A", null);
        when(examSittingRepository.findById(sittingId)).thenReturn(Optional.of(sitting));
    }

    @Test
    void reportsMisconductForAnExistingStudent() {
        when(studentDirectory.exists(studentId)).thenReturn(true);
        CurrentUser caller = new CurrentUser(
                staffUserId,
                "idp-subject",
                "invigilator",
                "ivy@example.edu",
                "Ivy Invigilator",
                Optional.empty(),
                Set.of("REGISTRAR"),
                Set.of());
        when(currentUserProvider.find()).thenReturn(Optional.of(caller));
        when(userDirectory.findById(staffUserId))
                .thenReturn(Optional.of(new UserDirectory.UserSummary(
                        staffUserId, "invigilator", "Ivy Invigilator", "ivy@example.edu", true)));
        when(examMisconductRecordRepository.save(any(ExamMisconductRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamMisconductRecordResponse response = service.reportMisconduct(
                sittingId, new ReportExamMisconductRequest(studentId, "Found with a phone during the paper."));

        assertThat(response.examSittingId()).isEqualTo(sittingId);
        assertThat(response.studentId()).isEqualTo(studentId);
        assertThat(response.reportedBy()).isEqualTo(staffUserId);
        assertThat(response.reportedByName()).isEqualTo("Ivy Invigilator");
        org.mockito.Mockito.verify(auditTrail)
                .record(
                        org.mockito.ArgumentMatchers.eq(staffUserId),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.Action.EXAM_MISCONDUCT_REPORTED),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(sittingId),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void refusesAReportAgainstAStudentThatDoesNotExist() {
        when(studentDirectory.exists(studentId)).thenReturn(false);

        assertThatThrownBy(() -> service.reportMisconduct(
                        sittingId, new ReportExamMisconductRequest(studentId, "Suspicious behaviour.")))
                .isInstanceOf(ResourceNotFoundException.class);

        org.mockito.Mockito.verify(examMisconductRecordRepository, org.mockito.Mockito.never())
                .save(any());
    }

    @Test
    void listsMisconductRecordsForASitting() {
        ExamMisconductRecord record =
                new ExamMisconductRecord(sittingId, studentId, "Unauthorised notes found.", staffUserId);
        when(examMisconductRecordRepository.findByExamSittingIdOrderByCreatedAtDesc(sittingId))
                .thenReturn(List.of(record));
        when(userDirectory.findById(staffUserId)).thenReturn(Optional.empty());

        List<ExamMisconductRecordResponse> responses = service.misconductFor(sittingId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).studentId()).isEqualTo(studentId);
        assertThat(responses.get(0).reportedByName()).isNull();
    }
}
