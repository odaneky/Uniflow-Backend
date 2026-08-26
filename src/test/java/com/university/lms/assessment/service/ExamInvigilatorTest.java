package com.university.lms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.assessment.domain.ExamInvigilator;
import com.university.lms.assessment.domain.ExamSitting;
import com.university.lms.assessment.dto.ExamInvigilatorResponse;
import com.university.lms.assessment.repository.ExamInvigilatorRepository;
import com.university.lms.assessment.repository.ExamMisconductRecordRepository;
import com.university.lms.assessment.repository.ExamResitCandidateRepository;
import com.university.lms.assessment.repository.ExamSittingRepository;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.api.Notifier;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExamInvigilatorTest {

    @Mock
    ExamSittingRepository examSittingRepository;

    @Mock
    ExamMisconductRecordRepository examMisconductRecordRepository;

    @Mock
    ExamInvigilatorRepository examInvigilatorRepository;

    @Mock
    ExamResitCandidateRepository examResitCandidateRepository;

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
    UUID lecturerUserId;

    @BeforeEach
    void setUp() {
        service = new ExamScheduleService(
                examSittingRepository,
                examMisconductRecordRepository,
                examInvigilatorRepository,
                examResitCandidateRepository,
                courseCatalog,
                enrollmentDirectory,
                studentDirectory,
                userDirectory,
                notifier,
                auditTrail,
                currentUserProvider);

        sittingId = UUID.randomUUID();
        lecturerUserId = UUID.randomUUID();

        ExamSitting sitting = new ExamSitting(UUID.randomUUID(), "Final", Instant.now(), 120, "Hall B", null);
        when(examSittingRepository.findById(sittingId)).thenReturn(Optional.of(sitting));
    }

    @Test
    void assignsAKnownUserAsInvigilator() {
        when(userDirectory.exists(lecturerUserId)).thenReturn(true);
        when(userDirectory.findById(lecturerUserId))
                .thenReturn(Optional.of(new UserDirectory.UserSummary(
                        lecturerUserId, "ivy", "Ivy Invigilator", "ivy@example.edu", true)));
        ExamInvigilator saved = new ExamInvigilator(sittingId, lecturerUserId, null);
        when(examInvigilatorRepository.findByExamSittingIdOrderByAssignedAtAsc(sittingId))
                .thenReturn(List.of(saved));

        List<ExamInvigilatorResponse> response = service.assignInvigilator(sittingId, lecturerUserId);

        verify(examInvigilatorRepository).save(any(ExamInvigilator.class));
        verify(auditTrail)
                .record(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.Action.EXAM_INVIGILATOR_ASSIGNED),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(sittingId),
                        org.mockito.ArgumentMatchers.anyString());
        assertThat(response).hasSize(1);
        assertThat(response.get(0).userId()).isEqualTo(lecturerUserId);
        assertThat(response.get(0).userName()).isEqualTo("Ivy Invigilator");
    }

    @Test
    void refusesToAssignAUserThatDoesNotExist() {
        when(userDirectory.exists(lecturerUserId)).thenReturn(false);

        assertThatThrownBy(() -> service.assignInvigilator(sittingId, lecturerUserId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(examInvigilatorRepository, never()).save(any());
    }

    @Test
    void assigningTheSameInvigilatorTwiceIsIdempotent() {
        when(userDirectory.exists(lecturerUserId)).thenReturn(true);
        when(examInvigilatorRepository.existsByExamSittingIdAndUserId(sittingId, lecturerUserId))
                .thenReturn(true);
        when(examInvigilatorRepository.findByExamSittingIdOrderByAssignedAtAsc(sittingId))
                .thenReturn(List.of(new ExamInvigilator(sittingId, lecturerUserId, null)));

        service.assignInvigilator(sittingId, lecturerUserId);

        verify(examInvigilatorRepository, never()).save(any());
        verify(auditTrail, never())
                .record(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(AuditTrail.Action.EXAM_INVIGILATOR_ASSIGNED),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unassignsAnAssignedInvigilator() {
        ExamInvigilator existing = new ExamInvigilator(sittingId, lecturerUserId, null);
        when(examInvigilatorRepository.findById(new ExamInvigilator.ExamInvigilatorId(sittingId, lecturerUserId)))
                .thenReturn(Optional.of(existing));
        when(examInvigilatorRepository.findByExamSittingIdOrderByAssignedAtAsc(sittingId)).thenReturn(List.of());

        List<ExamInvigilatorResponse> response = service.unassignInvigilator(sittingId, lecturerUserId);

        verify(examInvigilatorRepository).delete(existing);
        assertThat(response).isEmpty();
    }

    @Test
    void refusesToUnassignSomeoneNeverAssigned() {
        when(examInvigilatorRepository.findById(new ExamInvigilator.ExamInvigilatorId(sittingId, lecturerUserId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassignInvigilator(sittingId, lecturerUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
