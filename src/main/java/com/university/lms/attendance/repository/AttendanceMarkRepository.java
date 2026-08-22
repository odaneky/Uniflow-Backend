package com.university.lms.attendance.repository;

import com.university.lms.attendance.domain.AttendanceMark;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceMarkRepository extends JpaRepository<AttendanceMark, UUID> {

    List<AttendanceMark> findBySessionId(UUID sessionId);

    Optional<AttendanceMark> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    List<AttendanceMark> findBySessionIdIn(List<UUID> sessionIds);
}
