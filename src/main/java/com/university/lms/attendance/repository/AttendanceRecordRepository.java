package com.university.lms.attendance.repository;

import com.university.lms.attendance.domain.AttendanceRecord;
import com.university.lms.attendance.domain.AttendanceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the attendance module. */
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    List<AttendanceRecord> findByCourseSectionIdAndSessionDate(UUID courseSectionId, LocalDate sessionDate);

    Optional<AttendanceRecord> findByCourseSectionIdAndStudentIdAndSessionDate(
            UUID courseSectionId, UUID studentId, LocalDate sessionDate);

    long countByCourseSectionIdAndStudentIdAndStatus(UUID courseSectionId, UUID studentId, AttendanceStatus status);
}
