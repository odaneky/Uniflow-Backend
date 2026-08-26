package com.university.lms.disciplinary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.academic.domain.Programme;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.disciplinary.domain.DisciplinaryCategory;
import com.university.lms.disciplinary.domain.DisciplinaryCaseStatus;
import com.university.lms.disciplinary.domain.DisciplinaryOutcome;
import com.university.lms.disciplinary.dto.AssignCaseOfficerRequest;
import com.university.lms.disciplinary.dto.CreateDisciplinaryCaseNoteRequest;
import com.university.lms.disciplinary.dto.CreateDisciplinaryCaseRequest;
import com.university.lms.disciplinary.dto.DisciplinaryCaseResponse;
import com.university.lms.disciplinary.dto.ResolveDisciplinaryCaseRequest;
import com.university.lms.disciplinary.service.DisciplinaryCaseService;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.student.domain.Student;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * G7: proves confidentiality is per-case, not per-role — the filer and whoever the registry
 * assigns can read a case; every other staff member, however senior their role, is refused until
 * they are one of those two or hold the registry's own role.
 */
class DisciplinaryCaseIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DisciplinaryCaseService service;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private OwnerScopingFixtures ownerScopingFixtures;

    private UUID aStudent() {
        Programme programme = academicFixtures.programme();
        Student student = academicFixtures.student(programme);
        return student.getId();
    }

    @Test
    @DisplayName("the filer can read their own case; an uninvolved staff member cannot")
    void filerCanReadButAnUninvolvedStaffMemberCannot() throws Exception {
        UUID studentId = aStudent();
        OwnerScopingFixtures.Person filer = ownerScopingFixtures.lecturer();
        OwnerScopingFixtures.Person bystander = ownerScopingFixtures.lecturer();

        DisciplinaryCaseResponse filed = RunAs.as(
                filer.subject(),
                SecurityRoles.LECTURER,
                () -> service.fileCase(new CreateDisciplinaryCaseRequest(
                        studentId, DisciplinaryCategory.ACADEMIC_INTEGRITY, "Copied answers during a quiz")));

        assertThat(filed.status()).isEqualTo(DisciplinaryCaseStatus.OPEN);
        assertThat(filed.filedByUserId()).isEqualTo(filer.userId());
        assertThat(filed.caseNumber()).startsWith("DC-");

        DisciplinaryCaseResponse readByFiler =
                RunAs.as(filer.subject(), SecurityRoles.LECTURER, () -> service.find(filed.id()));
        assertThat(readByFiler.id()).isEqualTo(filed.id());

        assertThatThrownBy(() -> RunAs.as(bystander.subject(), SecurityRoles.LECTURER, () -> service.find(filed.id())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("the registry can assign an officer, who can then read and add notes; a bystander still cannot")
    void assignedOfficerGainsAccess() throws Exception {
        UUID studentId = aStudent();
        OwnerScopingFixtures.Person filer = ownerScopingFixtures.lecturer();
        OwnerScopingFixtures.Person officer = ownerScopingFixtures.lecturer();
        OwnerScopingFixtures.Person bystander = ownerScopingFixtures.lecturer();

        DisciplinaryCaseResponse filed = RunAs.as(
                filer.subject(),
                SecurityRoles.LECTURER,
                () -> service.fileCase(new CreateDisciplinaryCaseRequest(
                        studentId, DisciplinaryCategory.CONDUCT, "Disruptive in class")));

        DisciplinaryCaseResponse assigned = RunAs.staff(
                () -> service.assignOfficer(filed.id(), new AssignCaseOfficerRequest(officer.userId())));
        assertThat(assigned.status()).isEqualTo(DisciplinaryCaseStatus.UNDER_REVIEW);
        assertThat(assigned.assignedOfficerUserId()).isEqualTo(officer.userId());

        RunAs.as(
                officer.subject(),
                SecurityRoles.LECTURER,
                () -> service.addNote(filed.id(), new CreateDisciplinaryCaseNoteRequest("Met with the student.")));

        List<?> notes = RunAs.as(officer.subject(), SecurityRoles.LECTURER, () -> service.listNotes(filed.id()));
        assertThat(notes).hasSize(1);

        assertThatThrownBy(() -> RunAs.as(bystander.subject(), SecurityRoles.LECTURER, () -> service.find(filed.id())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("only the registry may assign a case officer")
    void onlyRegistryMayAssign() throws Exception {
        UUID studentId = aStudent();
        OwnerScopingFixtures.Person filer = ownerScopingFixtures.lecturer();
        OwnerScopingFixtures.Person someoneElse = ownerScopingFixtures.lecturer();

        DisciplinaryCaseResponse filed = RunAs.as(
                filer.subject(),
                SecurityRoles.LECTURER,
                () -> service.fileCase(
                        new CreateDisciplinaryCaseRequest(studentId, DisciplinaryCategory.OTHER, "Reported incident")));

        assertThatThrownBy(() -> RunAs.as(
                        filer.subject(),
                        SecurityRoles.LECTURER,
                        () -> service.assignOfficer(filed.id(), new AssignCaseOfficerRequest(someoneElse.userId()))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("closing a case requires a valid transition, an outcome, and a reason")
    void closingRequiresOutcomeAndReason() throws Exception {
        UUID studentId = aStudent();
        OwnerScopingFixtures.Person filer = ownerScopingFixtures.lecturer();

        DisciplinaryCaseResponse filed = RunAs.as(
                filer.subject(),
                SecurityRoles.LECTURER,
                () -> service.fileCase(new CreateDisciplinaryCaseRequest(
                        studentId, DisciplinaryCategory.HARASSMENT, "Reported by a peer")));

        DisciplinaryCaseResponse closed = RunAs.staff(() -> service.close(
                filed.id(),
                new ResolveDisciplinaryCaseRequest(
                        DisciplinaryCaseStatus.DISMISSED, DisciplinaryOutcome.NO_ACTION, "Insufficient evidence")));
        assertThat(closed.status()).isEqualTo(DisciplinaryCaseStatus.DISMISSED);
        assertThat(closed.resolvedAt()).isNotNull();

        assertThatThrownBy(() -> RunAs.staff(() -> service.close(
                        filed.id(),
                        new ResolveDisciplinaryCaseRequest(
                                DisciplinaryCaseStatus.RESOLVED, DisciplinaryOutcome.WARNING, "Too late, already closed"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("listing a student's cases only returns the ones the caller may actually read")
    void listForStudentFiltersToWhatTheCallerMayRead() throws Exception {
        UUID studentId = aStudent();
        OwnerScopingFixtures.Person filer = ownerScopingFixtures.lecturer();
        OwnerScopingFixtures.Person bystander = ownerScopingFixtures.lecturer();

        RunAs.as(
                filer.subject(),
                SecurityRoles.LECTURER,
                () -> service.fileCase(new CreateDisciplinaryCaseRequest(
                        studentId, DisciplinaryCategory.CONDUCT, "Filed by this lecturer")));

        List<DisciplinaryCaseResponse> asFiler =
                RunAs.as(filer.subject(), SecurityRoles.LECTURER, () -> service.listForStudent(studentId));
        assertThat(asFiler).hasSize(1);

        List<DisciplinaryCaseResponse> asBystander =
                RunAs.as(bystander.subject(), SecurityRoles.LECTURER, () -> service.listForStudent(studentId));
        assertThat(asBystander).isEmpty();

        List<DisciplinaryCaseResponse> asRegistrar = RunAs.staff(() -> service.listForStudent(studentId));
        assertThat(asRegistrar).hasSize(1);
    }
}
