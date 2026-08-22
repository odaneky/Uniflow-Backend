package com.university.lms.attendance.web;

import com.university.lms.attendance.dto.AttendanceDtos.AttendanceSessionDetailResponse;
import com.university.lms.attendance.dto.AttendanceDtos.CreateAttendanceSessionRequest;
import com.university.lms.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sections/{sectionId}/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<AttendanceSessionDetailResponse> list(
            @PathVariable UUID sectionId, @RequestParam(required = false) LocalDate sessionDate) {
        if (sessionDate != null) {
            return List.of(attendanceService.sessionOn(sectionId, sessionDate));
        }
        return attendanceService.sessionsOf(sectionId);
    }

    @PostMapping
    public ResponseEntity<AttendanceSessionDetailResponse> record(
            @PathVariable UUID sectionId, @Valid @RequestBody CreateAttendanceSessionRequest request) {
        AttendanceSessionDetailResponse created = attendanceService.recordSession(sectionId, request);
        return ResponseEntity.status(201).body(created);
    }
}
