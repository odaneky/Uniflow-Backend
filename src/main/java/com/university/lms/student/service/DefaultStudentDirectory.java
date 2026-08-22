package com.university.lms.student.service;

import com.university.lms.student.api.StudentDirectory;
import com.university.lms.student.domain.Student;
import com.university.lms.student.repository.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts the student module's internals to its published {@link StudentDirectory} contract. */
@Service
@Transactional(readOnly = true)
public class DefaultStudentDirectory implements StudentDirectory {

    private final StudentRepository studentRepository;

    public DefaultStudentDirectory(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public boolean exists(UUID studentId) {
        return studentId != null && studentRepository.existsById(studentId);
    }

    @Override
    public Optional<StudentSummary> findById(UUID studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        return studentRepository
                .findById(studentId)
                .map(student -> new StudentSummary(
                        student.getId(),
                        student.getUserId(),
                        student.getStudentNumber(),
                        student.getProgrammeId(),
                        student.canEnrol()));
    }

    @Override
    public boolean eligibleToEnrol(UUID studentId) {
        return findById(studentId).map(StudentSummary::eligibleToEnrol).orElse(false);
    }

    @Override
    public Optional<UUID> studentIdOfUser(UUID userId) {
        return studentRepository.findByUserId(userId).map(Student::getId);
    }

    @Override
    public Optional<UUID> advisorUserIdOf(UUID studentUserId) {
        return studentRepository
                .findByUserId(studentUserId)
                .map(Student::getAdvisorUserId)
                .filter(java.util.Objects::nonNull);
    }

    @Override
    public List<UUID> adviseeUserIdsOf(UUID advisorUserId) {
        if (advisorUserId == null) {
            return List.of();
        }
        return studentRepository.findByAdvisorUserId(advisorUserId, org.springframework.data.domain.Pageable.unpaged())
                .map(Student::getUserId)
                .toList();
    }

    @Override
    public List<UUID> userIdsByProgramme(UUID programmeId) {
        if (programmeId == null) {
            return List.of();
        }
        return studentRepository
                .search(null, programmeId, null, org.springframework.data.domain.Pageable.unpaged())
                .map(Student::getUserId)
                .toList();
    }
}
