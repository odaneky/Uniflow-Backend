package com.university.lms.student.service;

import com.university.lms.identity.spi.StudentNumberDirectory;
import com.university.lms.student.domain.Student;
import com.university.lms.student.repository.StudentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets the identity module correlate a login to a student record without knowing this module exists.
 *
 * <p>The port is declared by {@code identity} and implemented here because {@code student} owns
 * student numbers. Declared the other way round it would be a cycle: the student module already
 * depends on identity to answer "who is calling".
 *
 * <p>This is the join that makes a real student login work. The registry creates the student record
 * in advance; the identity provider asserts the same institutional number in the token; this maps
 * one to the other. Without it a student authenticates successfully and arrives with no academic
 * record at all.
 */
@Component
class StudentNumberDirectoryAdapter implements StudentNumberDirectory {

    private final StudentRepository studentRepository;

    StudentNumberDirectoryAdapter(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findUserIdByStudentNumber(String studentNumber) {
        return studentRepository.findByStudentNumber(studentNumber).map(Student::getUserId);
    }
}
