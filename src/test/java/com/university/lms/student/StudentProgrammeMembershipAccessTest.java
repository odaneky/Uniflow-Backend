package com.university.lms.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.academic.domain.Programme;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.student.domain.ProgrammeEnrolmentEndReason;
import com.university.lms.student.domain.ProgrammeEnrolmentKind;
import com.university.lms.student.dto.AddProgrammeMembershipRequest;
import com.university.lms.student.dto.EndProgrammeMembershipRequest;
import com.university.lms.student.dto.ProgrammeMembershipResponse;
import com.university.lms.student.service.StudentService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * G7 follow-up: {@code StudentService.addProgrammeMembership} and {@code endProgrammeMembership}
 * had no role guard of their own — {@code require(studentId)} is an existence check, not an
 * authorization one — and {@code SecurityConfig} had no POST matcher for
 * {@code /api/v1/students/*&#47;programmes} either (only the GET was covered by A3), so both fell
 * through to the final {@code anyRequest().authenticated()}. Any signed-in caller, a student
 * included, could add or end a secondary programme membership on <em>another</em> student's record,
 * with zero authorization check anywhere in the stack — the same shape as the disciplinary-cases
 * hole. Both are {@code @AccessClass(REGISTRY_ONLY)}; this pins that they are now enforced.
 */
class StudentProgrammeMembershipAccessTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private OwnerScopingFixtures ownerScopingFixtures;

    @Test
    @DisplayName("a student cannot add a secondary programme membership to another student's record")
    void aStudentCannotAddAProgrammeMembership() throws Exception {
        OwnerScopingFixtures.Person attacker = ownerScopingFixtures.student();
        OwnerScopingFixtures.Person victim = ownerScopingFixtures.student();
        Programme minor = academicFixtures.programme();

        assertThatThrownBy(() -> RunAs.as(
                        attacker.subject(),
                        SecurityRoles.STUDENT,
                        () -> studentService.addProgrammeMembership(
                                victim.studentId(),
                                new AddProgrammeMembershipRequest(
                                        minor.getId(), ProgrammeEnrolmentKind.MINOR, LocalDate.now()))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("a student cannot end a secondary programme membership on another student's record")
    void aStudentCannotEndAProgrammeMembership() throws Exception {
        OwnerScopingFixtures.Person attacker = ownerScopingFixtures.student();
        OwnerScopingFixtures.Person victim = ownerScopingFixtures.student();
        Programme minor = academicFixtures.programme();

        ProgrammeMembershipResponse membership = RunAs.staff(() -> studentService.addProgrammeMembership(
                victim.studentId(),
                new AddProgrammeMembershipRequest(minor.getId(), ProgrammeEnrolmentKind.MINOR, LocalDate.now())));
        assertThat(membership.primary()).isFalse();

        assertThatThrownBy(() -> RunAs.as(attacker.subject(), SecurityRoles.STUDENT, () -> {
                    studentService.endProgrammeMembership(
                            victim.studentId(),
                            membership.id(),
                            new EndProgrammeMembershipRequest(
                                    LocalDate.now(), ProgrammeEnrolmentEndReason.WITHDRAWN, "not their call"));
                    return null;
                }))
                .isInstanceOf(ForbiddenException.class);
    }
}
