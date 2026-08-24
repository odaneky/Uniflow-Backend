package com.university.lms.student.repository;

import com.university.lms.student.domain.StudentProgrammeEnrolment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the student module. */
public interface StudentProgrammeEnrolmentRepository extends JpaRepository<StudentProgrammeEnrolment, UUID> {

    Optional<StudentProgrammeEnrolment> findByStudentIdAndEndedOnIsNullAndPrimaryTrue(UUID studentId);

    List<StudentProgrammeEnrolment> findByStudentIdOrderByStartedOnAsc(UUID studentId);
}
