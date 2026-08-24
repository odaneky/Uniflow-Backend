package com.university.lms.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.domain.AdvisorOfficeHours;
import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentErrorCode;
import com.university.lms.student.domain.StudentStatus;
import com.university.lms.student.dto.AdvisorOfficeHoursResponse;
import com.university.lms.student.dto.CreateStudentRequest;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.dto.UpdateStudentRequest;
import com.university.lms.student.repository.AdvisorOfficeHoursRepository;
import com.university.lms.student.repository.StudentRepository;
import com.university.lms.student.service.StudentProgrammeEnrolmentService;
import com.university.lms.student.service.StudentService;
import java.time.LocalDate;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROGRAMME_ID = UUID.randomUUID();

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StudentProgrammeEnrolmentService programmeEnrolmentService;

    @Mock
    private AuditTrail auditTrail;

    @Mock
    private AdvisorOfficeHoursRepository advisorOfficeHoursRepository;

    @InjectMocks
    private StudentService service;

    private CreateStudentRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateStudentRequest(USER_ID, "20260001", PROGRAMME_ID, LocalDate.of(2026, 9, 1), null, null);
    }

    @Test
    void createsStudentWhenUserAndProgrammeExist() {
        givenValidReferences();
        when(studentRepository.existsByStudentNumber("20260001")).thenReturn(false);
        when(studentRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentResponse response = service.create(request);

        assertThat(response.studentNumber()).isEqualTo("20260001");
        assertThat(response.status()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(response.programmeId()).isEqualTo(PROGRAMME_ID);
    }

    @Test
    @DisplayName("a student number already in use is a 409, not a constraint stack trace")
    void rejectsDuplicateStudentNumber() {
        givenValidReferences();
        when(studentRepository.existsByStudentNumber("20260001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .satisfies(thrown -> assertThat(((ResourceAlreadyExistsException) thrown).getErrorCode())
                        .isEqualTo(StudentErrorCode.STUDENT_NUMBER_ALREADY_EXISTS));

        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("losing the unique-index race is still reported as a duplicate")
    void translatesConcurrentDuplicateIntoConflict() {
        givenValidReferences();
        when(studentRepository.existsByStudentNumber("20260001")).thenReturn(false);
        when(studentRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class)))
                .thenThrow(new DataIntegrityViolationException("uk_students_student_number"));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ResourceAlreadyExistsException.class);
    }

    @Test
    void rejectsUnknownUser() {
        when(userDirectory.exists(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(thrown -> assertThat(((ResourceNotFoundException) thrown).getErrorCode())
                        .isEqualTo(StudentErrorCode.STUDENT_USER_NOT_FOUND));
    }

    @Test
    void rejectsUnknownProgramme() {
        when(userDirectory.exists(USER_ID)).thenReturn(true);
        when(academicStructure.programmeExists(PROGRAMME_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(thrown -> assertThat(((ResourceNotFoundException) thrown).getErrorCode())
                        .isEqualTo(StudentErrorCode.STUDENT_PROGRAMME_NOT_FOUND));
    }

    @Test
    void findingAnUnknownStudentIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(studentRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknown)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a graduated record cannot be moved back to active")
    void refusesToReviveATerminalStanding() {
        Student student = new Student(USER_ID, "20260001", PROGRAMME_ID, LocalDate.of(2026, 9, 1));
        student.changeStatus(StudentStatus.GRADUATED);
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.update(
                        student.getId(),
                        new UpdateStudentRequest(
                                null, StudentStatus.ACTIVE, null, null, null, null, null, "Requesting reinstatement")))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> assertThat(((ValidationException) thrown).getErrorCode())
                        .isEqualTo(StudentErrorCode.INVALID_STUDENT_STATE));
    }

    @Test
    @DisplayName("a null field in a patch leaves the existing value alone")
    void partialUpdateLeavesOmittedFieldsUnchanged() {
        Student student = new Student(USER_ID, "20260001", PROGRAMME_ID, LocalDate.of(2026, 9, 1));
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));

        StudentResponse response = service.update(
                student.getId(),
                new UpdateStudentRequest(
                        null, StudentStatus.ON_LEAVE, null, null, null, null, null, "Approved medical leave"));

        assertThat(response.status()).isEqualTo(StudentStatus.ON_LEAVE);
        assertThat(response.programmeId()).isEqualTo(PROGRAMME_ID);
        assertThat(response.expectedGraduationDate()).isNull();
    }

    @Test
    @DisplayName("a status change without a reason is refused")
    void statusChangeWithoutReasonIsRefused() {
        Student student = new Student(USER_ID, "20260001", PROGRAMME_ID, LocalDate.of(2026, 9, 1));
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.update(
                        student.getId(),
                        new UpdateStudentRequest(null, StudentStatus.SUSPENDED, null, null, null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> assertThat(((ValidationException) thrown).getErrorCode())
                        .isEqualTo(StudentErrorCode.STUDENT_STATUS_REASON_REQUIRED));
    }

    @Test
    @DisplayName("a dismissed record is terminal — even reinstatement is refused")
    void dismissedIsTerminal() {
        Student student = new Student(USER_ID, "20260001", PROGRAMME_ID, LocalDate.of(2026, 9, 1));
        student.changeStatus(StudentStatus.DISMISSED);
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.update(
                        student.getId(),
                        new UpdateStudentRequest(
                                null, StudentStatus.ACTIVE, null, null, null, null, null, "Appeal granted")))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> assertThat(((ValidationException) thrown).getErrorCode())
                        .isEqualTo(StudentErrorCode.INVALID_STUDENT_STATE));
    }

    @Test
    @DisplayName("a withdrawn student may be readmitted")
    void withdrawnStudentMayBeReadmitted() {
        Student student = new Student(USER_ID, "20260001", PROGRAMME_ID, LocalDate.of(2026, 9, 1));
        student.changeStatus(StudentStatus.WITHDRAWN);
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));

        StudentResponse response = service.update(
                student.getId(),
                new UpdateStudentRequest(
                        null, StudentStatus.ACTIVE, null, null, null, null, null, "Readmitted after appeal"));

        assertThat(response.status()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    @DisplayName("G8: an advisor's office hours are stored once, not duplicated across every advisee")
    void advisorUpdatesOwnOfficeHours() {
        UUID advisorId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(
                        new CurrentUser(
                                advisorId,
                                "subject-advisor",
                                "advisor",
                                "advisor@university.test",
                                "Ada Advisor",
                                Optional.empty(),
                                Set.of(SecurityRoles.ACADEMIC_ADVISOR),
                                Set.of()));
        when(advisorOfficeHoursRepository.findByAdvisorUserId(advisorId)).thenReturn(Optional.empty());
        when(advisorOfficeHoursRepository.save(any(AdvisorOfficeHours.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdvisorOfficeHoursResponse response =
                service.updateOwnAdvisorOfficeHours("Tue 14:00–16:00 · Room 210");

        assertThat(response.officeHours()).isEqualTo("Tue 14:00–16:00 · Room 210");
        verify(advisorOfficeHoursRepository).save(any(AdvisorOfficeHours.class));
    }

    @Test
    @DisplayName("G8: assigning an advisor who already posted office hours shows them immediately")
    void newlyAssignedStudentSeesTheAdvisorsExistingOfficeHours() {
        Student student = new Student(USER_ID, "20260001", PROGRAMME_ID, LocalDate.of(2026, 9, 1));
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        UUID advisorId = UUID.randomUUID();
        when(userDirectory.exists(advisorId)).thenReturn(true);
        when(userDirectory.findByRealmRole(SecurityRoles.ACADEMIC_ADVISOR))
                .thenReturn(List.of(new UserDirectory.UserSummary(advisorId, "advisor", "Ada Advisor", "advisor@university.test", true)));
        when(advisorOfficeHoursRepository.findByAdvisorUserId(advisorId))
                .thenReturn(Optional.of(new AdvisorOfficeHours(advisorId, "Tue 14:00–16:00 · Room 210")));

        StudentResponse response = service.update(
                student.getId(),
                new UpdateStudentRequest(null, null, null, advisorId, null, null, null, null));

        assertThat(response.advisorOfficeHours()).isEqualTo("Tue 14:00–16:00 · Room 210");
    }

    private void givenValidReferences() {
        when(userDirectory.exists(USER_ID)).thenReturn(true);
        when(academicStructure.programmeExists(PROGRAMME_ID)).thenReturn(true);
    }
}
