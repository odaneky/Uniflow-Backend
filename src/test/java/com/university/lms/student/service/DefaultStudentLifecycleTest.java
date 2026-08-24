package com.university.lms.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.student.domain.Student;
import com.university.lms.student.repository.StudentRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * D5: the only path onto a student's programme used to be a registrar's direct, unreviewed PATCH.
 * {@link DefaultStudentLifecycle#transferProgramme} is what the new programme-transfer request
 * workflow applies on approval — it must change the same two things the direct path already does,
 * {@code Student.programmeId} and the temporal {@code student_programme_enrolments} record, in
 * lockstep.
 */
@ExtendWith(MockitoExtension.class)
class DefaultStudentLifecycleTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentService studentService;

    @Mock
    private StudentProgrammeEnrolmentService programmeEnrolmentService;

    @Mock
    private AcademicStructure academicStructure;

    @Test
    @DisplayName("transferring a programme updates the student record and opens a new temporal enrolment")
    void transferUpdatesBothTheStudentAndTheEnrolmentRecord() {
        DefaultStudentLifecycle lifecycle = new DefaultStudentLifecycle(
                studentRepository, studentService, programmeEnrolmentService, academicStructure);
        UUID oldProgrammeId = UUID.randomUUID();
        Student student = new Student(UUID.randomUUID(), "20260001", oldProgrammeId, LocalDate.of(2026, 9, 1));
        UUID newProgrammeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(academicStructure.programmeExists(newProgrammeId)).thenReturn(true);

        lifecycle.transferProgramme(student.getId(), newProgrammeId, "Changing majors", actorId);

        assertThat(student.getProgrammeId()).isEqualTo(newProgrammeId);
        verify(programmeEnrolmentService)
                .transfer(student.getId(), newProgrammeId, LocalDate.now(), "Changing majors", actorId);
    }

    @Test
    @DisplayName("transferring to a programme that no longer exists is refused")
    void transferToAMissingProgrammeIsRefused() {
        DefaultStudentLifecycle lifecycle = new DefaultStudentLifecycle(
                studentRepository, studentService, programmeEnrolmentService, academicStructure);
        Student student = new Student(UUID.randomUUID(), "20260001", UUID.randomUUID(), LocalDate.of(2026, 9, 1));
        UUID newProgrammeId = UUID.randomUUID();
        when(studentRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(academicStructure.programmeExists(newProgrammeId)).thenReturn(false);

        assertThatThrownBy(() ->
                        lifecycle.transferProgramme(student.getId(), newProgrammeId, "Changing majors", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
