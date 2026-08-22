package com.university.lms.attendance.service;

import com.university.lms.attendance.domain.AttendanceMark;
import com.university.lms.attendance.domain.AttendanceSession;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    public AttendanceService(
            AttendanceSessionRepository sessionRepository,
            AttendanceMarkRepository markRepository,
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            CurrentUserProvider currentUserProvider) {
        this.sessionRepository = sessionRepository;
        this.markRepository = markRepository;
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.currentUserProvider = currentUserProvider;
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

    private void requireTeacherOrAdmin(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN)) {
            courseCatalog
                    .findSection(sectionId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            CommonErrorCode.RESOURCE_NOT_FOUND, "No course section exists with id " + sectionId));
            return;
        }
        if (caller.hasRole(SecurityRoles.LECTURER) && courseCatalog.teaches(caller.userId(), sectionId)) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to manage attendance for this section");
    }
}
