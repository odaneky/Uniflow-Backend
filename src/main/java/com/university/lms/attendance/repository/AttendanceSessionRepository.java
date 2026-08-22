package com.university.lms.attendance.repository;

import com.university.lms.attendance.domain.AttendanceSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    List<AttendanceSession> findByCourseSectionIdOrderBySessionDateDesc(UUID courseSectionId);

    Optional<AttendanceSession> findByCourseSectionIdAndSessionDate(UUID courseSectionId, LocalDate sessionDate);
}
