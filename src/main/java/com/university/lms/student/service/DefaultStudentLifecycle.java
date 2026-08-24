package com.university.lms.student.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.student.api.StudentLifecycle;
import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentErrorCode;
import com.university.lms.student.domain.StudentStatus;
import com.university.lms.student.dto.UpdateOwnProfileRequest;
import com.university.lms.student.repository.StudentRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultStudentLifecycle implements StudentLifecycle {

    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final StudentProgrammeEnrolmentService programmeEnrolmentService;
    private final AcademicStructure academicStructure;

    public DefaultStudentLifecycle(
            StudentRepository studentRepository,
            @Lazy StudentService studentService,
            StudentProgrammeEnrolmentService programmeEnrolmentService,
            AcademicStructure academicStructure) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.programmeEnrolmentService = programmeEnrolmentService;
        this.academicStructure = academicStructure;
    }

    @Override
    public void graduate(UUID studentId, UUID actorUserId) {
        Student student = studentRepository
                .findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + studentId));
        if (student.getStatus() != StudentStatus.GRADUATED) {
            student.changeStatus(StudentStatus.GRADUATED);
        }
    }

    @Override
    public void applyContactCorrection(UUID studentId, UpdateOwnProfileRequest contact) {
        studentService.updateContactById(studentId, contact);
    }

    @Override
    public void beginLeave(UUID studentId, UUID actorUserId) {
        Student student = studentRepository
                .findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + studentId));
        if (student.getStatus() == StudentStatus.ACTIVE) {
            student.changeStatus(StudentStatus.ON_LEAVE);
        }
    }

    @Override
    public void readmit(UUID studentId, UUID actorUserId) {
        Student student = studentRepository
                .findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + studentId));
        if (student.getStatus() == StudentStatus.ON_LEAVE || student.getStatus() == StudentStatus.WITHDRAWN) {
            student.changeStatus(StudentStatus.ACTIVE);
        }
    }

    @Override
    public void transferProgramme(UUID studentId, UUID newProgrammeId, String reason, UUID actorUserId) {
        Student student = studentRepository
                .findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + studentId));
        if (!academicStructure.programmeExists(newProgrammeId)) {
            throw new ResourceNotFoundException(
                    StudentErrorCode.STUDENT_PROGRAMME_NOT_FOUND, "No programme exists with id " + newProgrammeId);
        }
        student.transferToProgramme(newProgrammeId);
        programmeEnrolmentService.transfer(studentId, newProgrammeId, LocalDate.now(), reason, actorUserId);
    }
}
