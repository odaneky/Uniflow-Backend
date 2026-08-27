package com.university.lms.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
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
class SectionRosterServiceTest {

    @Mock
    EnrollmentDirectory enrollmentDirectory;

    @Mock
    StudentDirectory studentDirectory;

    @Mock
    UserDirectory userDirectory;

    @Mock
    CourseCatalog courseCatalog;

    @Mock
    CurrentUserProvider currentUserProvider;

    SectionRosterService service;

    UUID sectionId;
    UUID studentId;
    UUID userId;

    @BeforeEach
    void setUp() {
        service = new SectionRosterService(
                enrollmentDirectory, studentDirectory, userDirectory, courseCatalog, currentUserProvider);

        sectionId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        userId = UUID.randomUUID();

        CurrentUser registrar = new CurrentUser(
                UUID.randomUUID(),
                "sub-reg",
                "reg",
                "reg@university.test",
                "Rita Registrar",
                Optional.empty(),
                Set.of(SecurityRoles.REGISTRAR),
                Set.of());
        when(currentUserProvider.require()).thenReturn(registrar);
        when(courseCatalog.findSection(sectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        sectionId, UUID.randomUUID(), "CMP1010", "Foundations", UUID.randomUUID(), "A", 30, 1, true,
                        null, false)));
        when(enrollmentDirectory.rosterOf(sectionId))
                .thenReturn(List.of(new EnrollmentDirectory.SectionEnrolment(
                        UUID.randomUUID(), studentId, sectionId, "ENROLLED")));
        lenient()
                .when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, userId, "20260099", UUID.randomUUID(), null, true,
                        ResidencyClassification.IN_DISTRICT)));
        lenient()
                .when(userDirectory.findById(userId))
                .thenReturn(Optional.of(
                        new UserDirectory.UserSummary(userId, "sam", "Sam Student", "sam@test.edu", true)));
    }

    @Test
    void rosterCsvHasAHeaderAndOneRowPerEntry() {
        String csv = service.rosterCsv(sectionId);

        List<String> lines = csv.lines().toList();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("Student Number,Full Name,Email,Status");
        assertThat(lines.get(1)).isEqualTo("\"20260099\",\"Sam Student\",\"sam@test.edu\",\"ENROLLED\"");
    }

    @Test
    void rosterCsvReconcilesSeatCountLikeTheJsonEndpoint() {
        service.rosterCsv(sectionId);

        org.mockito.Mockito.verify(enrollmentDirectory).reconcileSeatCount(sectionId);
    }

    @Test
    void rosterCsvQuotesAndEscapesEmbeddedCommasAndQuotes() {
        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, userId, "20260099", UUID.randomUUID(), null, true,
                        ResidencyClassification.IN_DISTRICT)));
        when(userDirectory.findById(userId))
                .thenReturn(Optional.of(
                        new UserDirectory.UserSummary(userId, "sam", "Doe, \"Sam\"", "sam@test.edu", true)));

        String csv = service.rosterCsv(sectionId);

        assertThat(csv).contains("\"Doe, \"\"Sam\"\"\"");
    }

    @Test
    void rosterCsvHandlesAStudentRecordThatNoLongerExists() {
        when(studentDirectory.findById(studentId)).thenReturn(Optional.empty());

        String csv = service.rosterCsv(sectionId);

        List<String> lines = csv.lines().toList();
        assertThat(lines.get(1)).isEqualTo("\"\",\"\",\"\",\"ENROLLED\"");
    }
}
