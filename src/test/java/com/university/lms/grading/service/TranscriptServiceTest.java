package com.university.lms.grading.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.repository.TransferCreditRepository;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.api.StudentDirectory;
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

/**
 * A7: an official transcript is a FERPA-relevant disclosure. It was being generated for staff
 * callers with no record of who exported it or when.
 */
@ExtendWith(MockitoExtension.class)
class TranscriptServiceTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID STUDENT_USER_ID = UUID.randomUUID();
    private static final UUID REGISTRAR_USER_ID = UUID.randomUUID();

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private TransferCreditRepository transferCreditRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private GradeService gradeService;

    @Mock
    private RecordAccessLog recordAccessLog;

    private TranscriptService service;

    @BeforeEach
    void setUp() {
        service = new TranscriptService(
                gradeRepository,
                courseCatalog,
                academicStructure,
                studentDirectory,
                transferCreditRepository,
                currentUserProvider,
                gradeService,
                recordAccessLog);
        lenient()
                .when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, STUDENT_USER_ID, "20260001", UUID.randomUUID(), true, ResidencyClassification.IN_DISTRICT)));
        lenient().when(gradeService.summaryOf(STUDENT_ID)).thenReturn(new AcademicRecord.Summary(null, 0, 0, 0));
        lenient().when(gradeRepository.findAllByStudentIdAndPublishedTrue(STUDENT_ID)).thenReturn(List.of());
        lenient().when(transferCreditRepository.findByStudentIdOrderByAwardedAtDesc(STUDENT_ID)).thenReturn(List.of());
    }

    private static CurrentUser registrar() {
        return new CurrentUser(
                REGISTRAR_USER_ID,
                "sub-registrar",
                "registrar",
                "registrar@university.test",
                "Rita Registrar",
                Optional.empty(),
                Set.of(SecurityRoles.REGISTRAR),
                Set.of());
    }

    private static CurrentUser student() {
        return new CurrentUser(
                STUDENT_USER_ID,
                "sub-student",
                "202012345",
                "student@university.test",
                "Sam Student",
                Optional.of("202012345"),
                Set.of(SecurityRoles.STUDENT),
                Set.of());
    }

    @Test
    @DisplayName("a registrar exporting a student's transcript is logged as a FERPA disclosure")
    void staffExportIsLogged() {
        when(currentUserProvider.require()).thenReturn(registrar());

        service.officialTranscript(STUDENT_ID);

        verify(recordAccessLog)
                .record(
                        eq(REGISTRAR_USER_ID),
                        eq("Rita Registrar"),
                        eq(STUDENT_ID),
                        eq(RecordAccessLog.RecordType.GRADES),
                        eq(RecordAccessLog.Action.EXPORT),
                        eq("Official transcript"));
    }

    @Test
    @DisplayName("a student viewing their own transcript is not logged as a disclosure")
    void ownAccessIsNotLogged() {
        when(currentUserProvider.require()).thenReturn(student());
        when(studentDirectory.studentIdOfUser(STUDENT_USER_ID)).thenReturn(Optional.of(STUDENT_ID));

        service.ownOfficialTranscript();

        verify(recordAccessLog, never())
                .record(any(UUID.class), anyString(), any(UUID.class), anyString(), anyString(), anyString());
    }
}
