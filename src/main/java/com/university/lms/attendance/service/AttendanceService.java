package com.university.lms.attendance.service;

import com.university.lms.attendance.domain.AttendanceMark;
import com.university.lms.attendance.domain.AttendanceSession;
import com.university.lms.attendance.domain.AttendanceStatus;
import com.university.lms.attendance.dto.AttendanceDtos.AtRiskStudentResponse;
import com.university.lms.attendance.dto.AttendanceDtos.AttendanceMarkResponse;
import com.university.lms.attendance.dto.AttendanceDtos.AttendanceSessionDetailResponse;
import com.university.lms.attendance.dto.AttendanceDtos.AttendanceSessionResponse;
import com.university.lms.attendance.dto.AttendanceDtos.CreateAttendanceSessionRequest;
import com.university.lms.attendance.repository.AttendanceMarkRepository;
import com.university.lms.attendance.repository.AttendanceSessionRepository;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.staffing.api.StaffAppointments;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceMarkRepository markRepository;
    private final CourseCatalog courseCatalog;
    private final EnrollmentDirectory enrollmentDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final StaffAppointments staffAppointments;

    public AttendanceService(
            AttendanceSessionRepository sessionRepository,
            AttendanceMarkRepository markRepository,
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            CurrentUserProvider currentUserProvider,
            StaffAppointments staffAppointments) {
        this.sessionRepository = sessionRepository;
        this.markRepository = markRepository;
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.currentUserProvider = currentUserProvider;
        this.staffAppointments = staffAppointments;
    }

    public List<AttendanceSessionDetailResponse> sessionsOf(UUID sectionId) {
        requireTeacherOrAdmin(sectionId);
        List<AttendanceSession> sessions = sessionRepository.findByCourseSectionIdOrderBySessionDateDesc(sectionId);
        List<UUID> sessionIds = sessions.stream().map(AttendanceSession::getId).toList();
        List<AttendanceMark> marks = sessionIds.isEmpty() ? List.of() : markRepository.findBySessionIdIn(sessionIds);
        return sessions.stream()
                .map(session -> new AttendanceSessionDetailResponse(
                        AttendanceSessionResponse.from(session),
                        marks.stream()
                                .filter(mark -> mark.getSessionId().equals(session.getId()))
                                .map(AttendanceMarkResponse::from)
                                .toList()))
                .toList();
    }

    public AttendanceSessionDetailResponse sessionOn(UUID sectionId, LocalDate sessionDate) {
        requireTeacherOrAdmin(sectionId);
        AttendanceSession session = sessionRepository
                .findByCourseSectionIdAndSessionDate(sectionId, sessionDate)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "No attendance session on " + sessionDate));
        List<AttendanceMarkResponse> marks = markRepository.findBySessionId(session.getId()).stream()
                .map(AttendanceMarkResponse::from)
                .toList();
        return new AttendanceSessionDetailResponse(AttendanceSessionResponse.from(session), marks);
    }

    @Transactional
    public AttendanceSessionDetailResponse recordSession(UUID sectionId, CreateAttendanceSessionRequest request) {
        CurrentUser caller = currentUserProvider.require();
        requireTeacherOrAdmin(sectionId);
        AttendanceSession session = sessionRepository
                .findByCourseSectionIdAndSessionDate(sectionId, request.sessionDate())
                .orElseGet(() -> new AttendanceSession(sectionId, request.sessionDate(), request.topic()));
        session.revise(request.topic());
        session.recordedBy(caller.userId());
        AttendanceSession savedSession = sessionRepository.save(session);
        List<AttendanceMarkResponse> marks = new ArrayList<>();
        var rosterStudentIds = enrollmentDirectory.rosterOf(sectionId).stream()
                .map(com.university.lms.enrollment.api.EnrollmentDirectory.SectionEnrolment::studentId)
                .collect(java.util.stream.Collectors.toSet());
        for (var markRequest : request.marks()) {
            if (!rosterStudentIds.contains(markRequest.studentId())) {
                throw new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED,
                        "Student " + markRequest.studentId() + " is not on this section roster");
            }
            AttendanceMark mark = markRepository
                    .findBySessionIdAndStudentId(savedSession.getId(), markRequest.studentId())
                    .orElseGet(() -> new AttendanceMark(
                            savedSession.getId(),
                            markRequest.studentId(),
                            markRequest.status(),
                            markRequest.note()));
            mark.correctTo(markRequest.status(), markRequest.note());
            marks.add(AttendanceMarkResponse.from(markRepository.save(mark)));
        }
        return new AttendanceSessionDetailResponse(AttendanceSessionResponse.from(savedSession), marks);
    }

    /**
     * G4: students whose attendance rate for this section is below {@code thresholdPercent}.
     *
     * <p>An {@code EXCUSED} mark is dropped from both the numerator and the denominator — an
     * excused absence should not count against the rate that flags a student as at risk. A student
     * with no considered marks yet is never flagged: no evidence of absence is not evidence of it.
     */
    public List<AtRiskStudentResponse> atRiskStudents(UUID sectionId, double thresholdPercent) {
        requireTeacherOrAdmin(sectionId);
        List<UUID> sessionIds = sessionRepository.findByCourseSectionIdOrderBySessionDateDesc(sectionId).stream()
                .map(AttendanceSession::getId)
                .toList();
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, int[]> countsByStudent = new LinkedHashMap<>();
        for (AttendanceMark mark : markRepository.findBySessionIdIn(sessionIds)) {
            int[] counts = countsByStudent.computeIfAbsent(mark.getStudentId(), id -> new int[4]);
            switch (mark.getStatus()) {
                case PRESENT -> counts[0]++;
                case LATE -> counts[1]++;
                case ABSENT -> counts[2]++;
                case EXCUSED -> counts[3]++;
            }
        }
        double threshold = thresholdPercent / 100.0;
        List<AtRiskStudentResponse> atRisk = new ArrayList<>();
        countsByStudent.forEach((studentId, counts) -> {
            int present = counts[0];
            int late = counts[1];
            int absent = counts[2];
            int excused = counts[3];
            int considered = present + late + absent;
            if (considered == 0) {
                return;
            }
            double rate = (double) (present + late) / considered;
            if (rate < threshold) {
                atRisk.add(new AtRiskStudentResponse(studentId, present, late, absent, excused, considered, rate));
            }
        });
        return atRisk;
    }

    private void requireTeacherOrAdmin(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "No course section exists with id " + sectionId));
        if (isAuthorizedAdmin(caller, section)) {
            return;
        }
        if (caller.hasRole(SecurityRoles.LECTURER) && courseCatalog.teaches(caller.userId(), sectionId)) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to manage attendance for this section");
    }

    /**
     * A5: SYSTEM_ADMIN/REGISTRAR/FACULTY_ADMIN previously bypassed section-department scoping
     * unconditionally here, the same {@code requireTeacherOrAdmin} over-reach already fixed in
     * {@code AssessmentService}, {@code LearningService} and {@code GradeService}. Deliberately a
     * separate check from LECTURER's, which stays gated on {@code courseCatalog.teaches},
     * unchanged. Same fail-open resolution and SYSTEM_ADMIN carve-out as the other A5 guards.
     */
    private boolean isAuthorizedAdmin(CurrentUser caller, CourseCatalog.SectionSummary section) {
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN))) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return true;
        }
        if (staffAppointments.activeAppointmentsOf(caller.userId()).isEmpty()) {
            return true;
        }
        Optional<UUID> orgUnitId = courseCatalog
                .departmentOfCourse(section.courseId())
                .flatMap(departmentId -> staffAppointments.orgUnitFor("DEPARTMENT", departmentId));
        return orgUnitId.isEmpty() || staffAppointments.isAppointedOver(caller.userId(), orgUnitId.get());
    }
}
