package com.university.lms.student.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentErrorCode;
import com.university.lms.student.domain.StudentStatus;
import com.university.lms.student.dto.AdviseeSummaryResponse;
import com.university.lms.student.dto.AdvisorCandidateResponse;
import com.university.lms.student.dto.AdvisorOfficeHoursResponse;
import com.university.lms.student.dto.CreateStudentRequest;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.dto.StudentSummaryResponse;
import com.university.lms.student.dto.UpdateOwnProfileRequest;
import com.university.lms.student.dto.UpdateStudentRequest;
import com.university.lms.student.repository.StudentRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for student records.
 *
 * <p>Depends on {@link UserDirectory} and {@link AcademicStructure} — the published contracts of
 * the identity and academic modules — rather than their repositories, so this module compiles
 * against their APIs alone.
 */
@Service
@Transactional(readOnly = true)
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final UserDirectory userDirectory;
    private final AcademicStructure academicStructure;

    private final CurrentUserProvider currentUserProvider;
    private final RecordAccessLog recordAccessLog;
    private final StudentProgrammeEnrolmentService programmeEnrolmentService;
    private final AuditTrail auditTrail;

    public StudentService(
            StudentRepository studentRepository,
            UserDirectory userDirectory,
            AcademicStructure academicStructure,
            CurrentUserProvider currentUserProvider,
            RecordAccessLog recordAccessLog,
            StudentProgrammeEnrolmentService programmeEnrolmentService,
            AuditTrail auditTrail) {
        this.studentRepository = studentRepository;
        this.userDirectory = userDirectory;
        this.academicStructure = academicStructure;
        this.currentUserProvider = currentUserProvider;
        this.recordAccessLog = recordAccessLog;
        this.programmeEnrolmentService = programmeEnrolmentService;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public StudentResponse create(CreateStudentRequest request) {
        if (!userDirectory.exists(request.userId())) {
            throw new ResourceNotFoundException(
                    StudentErrorCode.STUDENT_USER_NOT_FOUND, "No user exists with id " + request.userId());
        }
        if (!academicStructure.programmeExists(request.programmeId())) {
            throw new ResourceNotFoundException(
                    StudentErrorCode.STUDENT_PROGRAMME_NOT_FOUND,
                    "No programme exists with id " + request.programmeId());
        }
        if (studentRepository.existsByStudentNumber(request.studentNumber())) {
            throw new ResourceAlreadyExistsException(
                    StudentErrorCode.STUDENT_NUMBER_ALREADY_EXISTS,
                    "Student number " + request.studentNumber() + " is already in use");
        }
        if (studentRepository.existsByUserId(request.userId())) {
            throw new ResourceAlreadyExistsException(
                    StudentErrorCode.STUDENT_ALREADY_EXISTS_FOR_USER,
                    "A student record already exists for user " + request.userId());
        }

        Student student = new Student(
                request.userId(), request.studentNumber(), request.programmeId(), request.admissionDate());
        if (request.expectedGraduationDate() != null) {
            student.expectGraduationOn(request.expectedGraduationDate());
        }

        try {
            Student saved = studentRepository.saveAndFlush(student);
            programmeEnrolmentService.openInitial(saved.getId(), request.programmeId(), request.admissionDate());
            log.info("Created student {} ({})", saved.getStudentNumber(), saved.getId());
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    StudentErrorCode.STUDENT_NUMBER_ALREADY_EXISTS,
                    "Student number " + request.studentNumber() + " is already in use",
                    ex);
        }
    }

    public StudentResponse findById(UUID studentId) {
        Student student = require(studentId);
        CurrentUser caller = currentUserProvider.require();
        caller.requireSelfOrStaff(student.getUserId());
        logStaffRecordAccess(caller, student.getId(), RecordAccessLog.Action.VIEW, "Student record");
        return toResponse(student);
    }

    public StudentResponse findByStudentNumber(String studentNumber) {
        Student student = studentRepository
                .findByStudentNumber(studentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with number " + studentNumber));
        CurrentUser caller = currentUserProvider.require();
        caller.requireSelfOrStaff(student.getUserId());
        logStaffRecordAccess(caller, student.getId(), RecordAccessLog.Action.VIEW, "Student record by number");
        return toResponse(student);
    }

    public StudentResponse findOwn() {
        UUID userId = currentUserProvider.require().userId();
        return studentRepository
                .findByUserId(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "You do not have a student record"));
    }

    public PageResponse<StudentSummaryResponse> search(
            StudentStatus status, UUID programmeId, UUID advisorUserId, Pageable pageable) {
        if (!currentUserProvider.require().isStaff()) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to list student records");
        }
        return PageResponse.from(
                studentRepository.search(status, programmeId, advisorUserId, pageable), this::toSummary);
    }

    /** Staff with the Academic Advisor role — lecturers alone cannot be assigned. */
    public List<AdvisorCandidateResponse> listAdvisorCandidates() {
        if (!currentUserProvider.require().isStaff()) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to list advisors");
        }
        List<AdvisorCandidateResponse> rows = new ArrayList<>();
        for (UserDirectory.UserSummary user : userDirectory.findByRealmRole(SecurityRoles.ACADEMIC_ADVISOR)) {
            rows.add(new AdvisorCandidateResponse(user.id(), user.fullName(), user.email(), "Academic advisor"));
        }
        rows.sort(Comparator.comparing(r -> r.fullName() == null ? "" : r.fullName(), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Students assigned to the caller as advisees (Academic Advisor role only). */
    public PageResponse<AdviseeSummaryResponse> listOwnAdvisees(Pageable pageable) {
        CurrentUser caller = currentUserProvider.require();
        if (!caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "Only academic advisors can list advisees");
        }
        return PageResponse.from(
                studentRepository.findByAdvisorUserId(caller.userId(), pageable), this::toAdvisee);
    }

    private AdviseeSummaryResponse toAdvisee(Student student) {
        var user = userDirectory.findById(student.getUserId());
        return new AdviseeSummaryResponse(
                student.getId(),
                student.getStudentNumber(),
                user.map(UserDirectory.UserSummary::fullName).orElse(null),
                user.map(UserDirectory.UserSummary::email).orElse(null),
                student.getProgrammeId(),
                student.getStatus().name());
    }

    private StudentSummaryResponse toSummary(Student student) {
        var user = userDirectory.findById(student.getUserId());
        return StudentSummaryResponse.from(
                student,
                user.map(UserDirectory.UserSummary::fullName).orElse(null),
                user.map(UserDirectory.UserSummary::email).orElse(null));
    }

    private StudentResponse toResponse(Student student) {
        if (student.getAdvisorUserId() == null) {
            return StudentResponse.from(student);
        }
        var advisor = userDirectory.findById(student.getAdvisorUserId());
        return StudentResponse.from(
                student,
                advisor.map(UserDirectory.UserSummary::fullName).orElse(null),
                advisor.map(UserDirectory.UserSummary::email).orElse(null));
    }

    @Transactional
    public StudentResponse update(UUID studentId, UpdateStudentRequest request) {
        Student student = require(studentId);

        if (request.programmeId() != null) {
            if (!academicStructure.programmeExists(request.programmeId())) {
                throw new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_PROGRAMME_NOT_FOUND,
                        "No programme exists with id " + request.programmeId());
            }
            student.transferToProgramme(request.programmeId());
            programmeEnrolmentService.transfer(
                    student.getId(),
                    request.programmeId(),
                    LocalDate.now(),
                    null,
                    currentUserProvider.find().map(CurrentUser::userId).orElse(null));
        }
        if (request.status() != null) {
            applyStatusChange(student, request.status(), request.reason());
        }
        if (request.expectedGraduationDate() != null) {
            student.expectGraduationOn(request.expectedGraduationDate());
        }
        if (Boolean.TRUE.equals(request.clearAdvisor())) {
            student.clearAdvisor();
        } else if (request.advisorUserId() != null) {
            requireAssignableAdvisor(request.advisorUserId());
            UUID nextAdvisor = request.advisorUserId();
            String hours =
                    nextAdvisor.equals(student.getAdvisorUserId()) ? student.getAdvisorOfficeHours() : null;
            student.assignAdvisor(nextAdvisor, hours);
        }
        if (request.contact() != null) {
            applyContact(student, request.contact());
        }

        return toResponse(student);
    }

    /** Registry correction of contact details on a student record. */
    @Transactional
    public void updateContactById(UUID studentId, UpdateOwnProfileRequest request) {
        applyContact(require(studentId), request);
    }

    /** Office hours the caller has posted for their advisees. */
    public AdvisorOfficeHoursResponse findOwnAdvisorOfficeHours() {
        CurrentUser caller = requireAdvisor();
        return studentRepository.findAllByAdvisorUserId(caller.userId()).stream()
                .map(Student::getAdvisorOfficeHours)
                .filter(h -> h != null && !h.isBlank())
                .findFirst()
                .map(AdvisorOfficeHoursResponse::new)
                .orElseGet(() -> new AdvisorOfficeHoursResponse(null));
    }

    /** Updates office hours on every student record assigned to the caller. */
    @Transactional
    public AdvisorOfficeHoursResponse updateOwnAdvisorOfficeHours(String officeHours) {
        CurrentUser caller = requireAdvisor();
        String normalized = blankToNull(officeHours);
        for (Student student : studentRepository.findAllByAdvisorUserId(caller.userId())) {
            student.setAdvisorOfficeHours(normalized);
        }
        return new AdvisorOfficeHoursResponse(normalized);
    }

    private CurrentUser requireAdvisor() {
        CurrentUser caller = currentUserProvider.require();
        if (!caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "Only academic advisors can manage office hours");
        }
        return caller;
    }

    @Transactional
    public StudentResponse updateOwnProfile(UpdateOwnProfileRequest request) {
        UUID userId = currentUserProvider.require().userId();
        Student student = studentRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "You do not have a student record"));
        applyContact(student, request);
        return toResponse(student);
    }

    private void requireAssignableAdvisor(UUID advisorUserId) {
        if (!userDirectory.exists(advisorUserId)) {
            throw new ResourceNotFoundException(
                    StudentErrorCode.STUDENT_USER_NOT_FOUND, "No user exists with id " + advisorUserId);
        }
        boolean advisor = userDirectory.findByRealmRole(SecurityRoles.ACADEMIC_ADVISOR).stream()
                .anyMatch(u -> u.id().equals(advisorUserId));
        if (!advisor) {
            throw new ValidationException(
                    StudentErrorCode.INVALID_STUDENT_STATE,
                    "Advisor must hold the Academic Advisor role");
        }
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void applyStatusChange(Student student, StudentStatus target, String reason) {
        StudentStatus current = student.getStatus();
        if (current == target) {
            return;
        }
        if (!current.canTransitionTo(target)) {
            throw new ValidationException(
                    StudentErrorCode.INVALID_STUDENT_STATE,
                    "A " + current + " student record cannot be changed to " + target);
        }
        if (reason == null || reason.isBlank()) {
            throw new ValidationException(
                    StudentErrorCode.STUDENT_STATUS_REASON_REQUIRED,
                    "A reason is required to change a student's status");
        }
        student.changeStatus(target);
        CurrentUser actor = currentUserProvider.find().orElse(null);
        auditTrail.record(
                actor == null ? null : actor.userId(),
                actor == null ? null : actorLabel(actor),
                AuditTrail.Action.STUDENT_STATUS_CHANGED,
                AuditTrail.EntityType.STUDENT,
                student.getId(),
                student.getStudentNumber() + ": " + current + " → " + target,
                reason,
                current.name(),
                target.name());
    }

    private static String actorLabel(CurrentUser actor) {
        if (actor.fullName() != null && !actor.fullName().isBlank()) {
            return actor.fullName();
        }
        return actor.username();
    }

    private static void applyContact(Student student, UpdateOwnProfileRequest request) {
        var profile = student.getProfile();
        if (request.personalEmail() != null) {
            profile.updatePersonalEmail(blankToNull(request.personalEmail()));
        }
        if (request.gender() != null) {
            profile.updateGender(blankToNull(request.gender()));
        }
        profile.updateContact(
                request.phoneNumber() != null ? request.phoneNumber() : profile.getPhoneNumber(),
                request.nationality() != null ? request.nationality() : profile.getNationality(),
                request.dateOfBirth() != null ? request.dateOfBirth() : profile.getDateOfBirth());
        profile.updateAddress(
                request.addressLine1() != null ? request.addressLine1() : profile.getAddressLine1(),
                request.addressLine2() != null ? request.addressLine2() : profile.getAddressLine2(),
                request.city() != null ? request.city() : profile.getCity(),
                request.country() != null ? request.country() : profile.getCountry());
        profile.updateEmergencyContact(
                request.emergencyContactName() != null
                        ? request.emergencyContactName()
                        : profile.getEmergencyContactName(),
                request.emergencyContactPhone() != null
                        ? request.emergencyContactPhone()
                        : profile.getEmergencyContactPhone());
    }

    private void logStaffRecordAccess(CurrentUser caller, UUID studentId, String action, String details) {
        if (!caller.isStaff()) {
            return;
        }
        recordAccessLog.record(
                caller.userId(),
                caller.fullName(),
                studentId,
                RecordAccessLog.RecordType.STUDENT,
                action,
                details);
    }

    private Student require(UUID studentId) {
        return studentRepository
                .findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + studentId));
    }
}
