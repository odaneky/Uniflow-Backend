package com.university.lms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.attendance.domain.AttendanceMark;
import com.university.lms.attendance.domain.AttendanceSession;
import com.university.lms.attendance.domain.AttendanceStatus;
import com.university.lms.attendance.dto.AttendanceDtos.AtRiskStudentResponse;
import com.university.lms.attendance.repository.AttendanceMarkRepository;
import com.university.lms.attendance.repository.AttendanceSessionRepository;
import com.university.lms.attendance.service.AttendanceService;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.staffing.api.StaffAppointments;
import java.time.LocalDate;
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
class AttendanceAtRiskTest {

    @Mock
    AttendanceSessionRepository sessionRepository;

    @Mock
    AttendanceMarkRepository markRepository;

    @Mock
    CourseCatalog courseCatalog;

    @Mock
    EnrollmentDirectory enrollmentDirectory;

    @Mock
    CurrentUserProvider currentUserProvider;

    @Mock
    StaffAppointments staffAppointments;

    AttendanceService service;

    UUID sectionId;
    UUID belowThresholdStudent;
    UUID aboveThresholdStudent;
    UUID excusedOnlyStudent;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(
                sessionRepository, markRepository, courseCatalog, enrollmentDirectory, currentUserProvider,
                staffAppointments);
        sectionId = UUID.randomUUID();
        belowThresholdStudent = UUID.randomUUID();
        aboveThresholdStudent = UUID.randomUUID();
        excusedOnlyStudent = UUID.randomUUID();

        CurrentUser registrar = new CurrentUser(
                UUID.randomUUID(),
                "idp-subject",
                "registrar",
                "registrar@example.edu",
                "Regina Registrar",
                Optional.empty(),
                Set.of(SecurityRoles.REGISTRAR),
                Set.of());
        when(currentUserProvider.require()).thenReturn(registrar);
        when(courseCatalog.findSection(sectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        sectionId, UUID.randomUUID(), "CMP1024", "A", UUID.randomUUID(), "A", 30, 10, true, null,
                        false)));

        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();
        UUID session3 = UUID.randomUUID();
        UUID session4 = UUID.randomUUID();
        when(sessionRepository.findByCourseSectionIdOrderBySessionDateDesc(sectionId))
                .thenReturn(List.of(
                        new AttendanceSession(sectionId, LocalDate.of(2026, 1, 1), null),
                        new AttendanceSession(sectionId, LocalDate.of(2026, 1, 8), null),
                        new AttendanceSession(sectionId, LocalDate.of(2026, 1, 15), null),
                        new AttendanceSession(sectionId, LocalDate.of(2026, 1, 22), null)));

        // belowThresholdStudent: 1 present, 3 absent -> 25% attended, below a 75% threshold.
        // aboveThresholdStudent: 3 present, 1 absent -> 75% attended, meets a 75% threshold.
        // excusedOnlyStudent: 1 present, 3 excused -> 100% of considered sessions attended.
        when(markRepository.findBySessionIdIn(any()))
                .thenReturn(List.of(
                        new AttendanceMark(session1, belowThresholdStudent, AttendanceStatus.PRESENT, null),
                        new AttendanceMark(session2, belowThresholdStudent, AttendanceStatus.ABSENT, null),
                        new AttendanceMark(session3, belowThresholdStudent, AttendanceStatus.ABSENT, null),
                        new AttendanceMark(session4, belowThresholdStudent, AttendanceStatus.ABSENT, null),
                        new AttendanceMark(session1, aboveThresholdStudent, AttendanceStatus.PRESENT, null),
                        new AttendanceMark(session2, aboveThresholdStudent, AttendanceStatus.PRESENT, null),
                        new AttendanceMark(session3, aboveThresholdStudent, AttendanceStatus.LATE, null),
                        new AttendanceMark(session4, aboveThresholdStudent, AttendanceStatus.ABSENT, null),
                        new AttendanceMark(session1, excusedOnlyStudent, AttendanceStatus.PRESENT, null),
                        new AttendanceMark(session2, excusedOnlyStudent, AttendanceStatus.EXCUSED, null),
                        new AttendanceMark(session3, excusedOnlyStudent, AttendanceStatus.EXCUSED, null),
                        new AttendanceMark(session4, excusedOnlyStudent, AttendanceStatus.EXCUSED, null)));
    }

    @Test
    void flagsOnlyStudentsBelowTheThreshold() {
        List<AtRiskStudentResponse> atRisk = service.atRiskStudents(sectionId, 75);

        assertThat(atRisk).extracting(AtRiskStudentResponse::studentId).containsExactly(belowThresholdStudent);
        AtRiskStudentResponse flagged = atRisk.get(0);
        assertThat(flagged.presentCount()).isEqualTo(1);
        assertThat(flagged.absentCount()).isEqualTo(3);
        assertThat(flagged.consideredSessions()).isEqualTo(4);
        assertThat(flagged.attendanceRate()).isCloseTo(0.25, within(0.0001));
    }

    @Test
    void excusedAbsencesAreDroppedFromTheRateEntirely() {
        List<AtRiskStudentResponse> atRisk = service.atRiskStudents(sectionId, 75);

        assertThat(atRisk).extracting(AtRiskStudentResponse::studentId).doesNotContain(excusedOnlyStudent);
    }

    @Test
    void aStudentWithNoConsideredMarksIsNeverFlagged() {
        UUID noDataStudent = UUID.randomUUID();
        when(markRepository.findBySessionIdIn(any())).thenReturn(List.of());

        List<AtRiskStudentResponse> atRisk = service.atRiskStudents(sectionId, 75);

        assertThat(atRisk).isEmpty();
    }
}
