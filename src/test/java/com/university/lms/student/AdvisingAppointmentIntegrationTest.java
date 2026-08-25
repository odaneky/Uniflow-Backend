package com.university.lms.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.academic.domain.Programme;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.student.domain.Student;
import com.university.lms.student.dto.AdvisingAppointmentResponse;
import com.university.lms.student.dto.CancelAdvisingAppointmentRequest;
import com.university.lms.student.dto.CreateAdvisingAppointmentRequest;
import com.university.lms.student.repository.StudentRepository;
import com.university.lms.student.service.StudentService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * G8: advising had notes but no way to actually schedule a meeting, or for a student to see one
 * coming up. Proves an assigned advisor can schedule and cancel an appointment, the student sees it
 * on their own list, and an advisor not assigned to that student is refused.
 */
class AdvisingAppointmentIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private OwnerScopingFixtures ownerScopingFixtures;

    @Autowired
    private StudentRepository studentRepository;

    private Student studentWithAdvisor(OwnerScopingFixtures.Person advisor) {
        Programme programme = academicFixtures.programme();
        Student student = academicFixtures.student(programme);
        student.assignAdvisor(advisor.userId());
        return studentRepository.saveAndFlush(student);
    }

    @Test
    @DisplayName("the assigned advisor can schedule an appointment, and it appears on the staff listing")
    void assignedAdvisorSchedulesAndStudentSeesIt() throws Exception {
        OwnerScopingFixtures.Person advisor = ownerScopingFixtures.lecturer();
        Student student = studentWithAdvisor(advisor);
        Instant when = Instant.now().plusSeconds(3600);

        AdvisingAppointmentResponse scheduled = RunAs.as(
                advisor.subject(),
                SecurityRoles.ACADEMIC_ADVISOR,
                () -> studentService.scheduleAdvisingAppointment(
                        student.getId(), new CreateAdvisingAppointmentRequest(when, 30, "Registration check-in")));

        assertThat(scheduled.cancelled()).isFalse();
        assertThat(scheduled.durationMinutes()).isEqualTo(30);
        assertThat(scheduled.advisorUserId()).isEqualTo(advisor.userId());

        List<AdvisingAppointmentResponse> staffView = RunAs.as(
                advisor.subject(),
                SecurityRoles.ACADEMIC_ADVISOR,
                () -> studentService.listAdvisingAppointments(student.getId()));
        assertThat(staffView).hasSize(1);
    }

    @Test
    @DisplayName("cancelling an appointment marks it cancelled with a reason")
    void cancellingMarksItCancelled() throws Exception {
        OwnerScopingFixtures.Person advisor = ownerScopingFixtures.lecturer();
        Student student = studentWithAdvisor(advisor);

        AdvisingAppointmentResponse scheduled = RunAs.as(
                advisor.subject(),
                SecurityRoles.ACADEMIC_ADVISOR,
                () -> studentService.scheduleAdvisingAppointment(
                        student.getId(),
                        new CreateAdvisingAppointmentRequest(Instant.now().plusSeconds(7200), 45, null)));

        AdvisingAppointmentResponse cancelled = RunAs.as(
                advisor.subject(),
                SecurityRoles.ACADEMIC_ADVISOR,
                () -> studentService.cancelAdvisingAppointment(
                        scheduled.id(), new CancelAdvisingAppointmentRequest("Student rescheduled")));

        assertThat(cancelled.cancelled()).isTrue();
        assertThat(cancelled.cancelledReason()).isEqualTo("Student rescheduled");
    }

    @Test
    @DisplayName("an advisor who is not assigned to the student is refused")
    void unassignedAdvisorRefused() throws Exception {
        OwnerScopingFixtures.Person assignedAdvisor = ownerScopingFixtures.lecturer();
        OwnerScopingFixtures.Person otherAdvisor = ownerScopingFixtures.lecturer();
        Student student = studentWithAdvisor(assignedAdvisor);

        assertThatThrownBy(() -> RunAs.as(
                        otherAdvisor.subject(),
                        SecurityRoles.ACADEMIC_ADVISOR,
                        () -> studentService.scheduleAdvisingAppointment(
                                student.getId(),
                                new CreateAdvisingAppointmentRequest(Instant.now().plusSeconds(3600), 30, null))))
                .isInstanceOf(ForbiddenException.class);
    }
}
