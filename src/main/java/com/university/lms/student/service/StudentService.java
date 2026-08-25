package com.university.lms.student.service;

import com.university.lms.administration.api.Auditable;
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
import com.university.lms.student.dto.AddProgrammeMembershipRequest;
import com.university.lms.student.dto.AdviseeSummaryResponse;
import com.university.lms.student.dto.AdvisingAppointmentResponse;
import com.university.lms.student.dto.AdvisingNoteResponse;
import com.university.lms.student.dto.AdvisorCandidateResponse;
import com.university.lms.student.dto.AdvisorOfficeHoursResponse;
import com.university.lms.student.dto.CancelAdvisingAppointmentRequest;
import com.university.lms.student.dto.CreateAdvisingAppointmentRequest;
import com.university.lms.student.dto.CreateAdvisingNoteRequest;
import com.university.lms.student.dto.CreateStudentRequest;
import com.university.lms.student.dto.EndProgrammeMembershipRequest;
import com.university.lms.student.dto.ProgrammeMembershipResponse;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.dto.StudentSummaryResponse;
import com.university.lms.student.dto.UpdateOwnProfileRequest;
import com.university.lms.student.dto.UpdateStudentRequest;
import com.university.lms.student.domain.AdvisingAppointment;
import com.university.lms.student.domain.AdvisingNote;
import com.university.lms.student.domain.AdvisorOfficeHours;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.repository.AdvisingAppointmentRepository;
import com.university.lms.student.repository.AdvisingNoteRepository;
import com.university.lms.student.repository.AdvisorOfficeHoursRepository;
import com.university.lms.student.repository.StudentRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    private final AdvisorOfficeHoursRepository advisorOfficeHoursRepository;
    private final AdvisingNoteRepository advisingNoteRepository;
    private final AdvisingAppointmentRepository advisingAppointmentRepository;
    private final StaffAppointments staffAppointments;

    public StudentService(
            StudentRepository studentRepository,
            UserDirectory userDirectory,
            AcademicStructure academicStructure,
            CurrentUserProvider currentUserProvider,
            RecordAccessLog recordAccessLog,
            StudentProgrammeEnrolmentService programmeEnrolmentService,
            AuditTrail auditTrail,
            AdvisorOfficeHoursRepository advisorOfficeHoursRepository,
            AdvisingNoteRepository advisingNoteRepository,
            AdvisingAppointmentRepository advisingAppointmentRepository,
            StaffAppointments staffAppointments) {
        this.studentRepository = studentRepository;
        this.userDirectory = userDirectory;
        this.academicStructure = academicStructure;
        this.currentUserProvider = currentUserProvider;
        this.recordAccessLog = recordAccessLog;
        this.programmeEnrolmentService = programmeEnrolmentService;
        this.auditTrail = auditTrail;
        this.advisorOfficeHoursRepository = advisorOfficeHoursRepository;
        this.advisingNoteRepository = advisingNoteRepository;
        this.advisingAppointmentRepository = advisingAppointmentRepository;
        this.staffAppointments = staffAppointments;
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
        if (request.residencyClassification() != null) {
            student.reclassifyResidency(request.residencyClassification());
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
        requireSelfOrAuthorizedStaff(caller, student);
        logStaffRecordAccess(caller, student.getId(), RecordAccessLog.Action.VIEW, "Student record");
        return toResponse(student);
    }

    public StudentResponse findByStudentNumber(String studentNumber) {
        Student student = studentRepository
                .findByStudentNumber(studentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with number " + studentNumber));
        CurrentUser caller = currentUserProvider.require();
        requireSelfOrAuthorizedStaff(caller, student);
        logStaffRecordAccess(caller, student.getId(), RecordAccessLog.Action.VIEW, "Student record by number");
        return toResponse(student);
    }

    /**
     * A5: {@code CurrentUser.requireSelfOrStaff} is a blind "self or any staff role" check shared
     * across the codebase — it cannot be narrowed in place without breaking every other caller, so
     * this replaces its use here with an equivalent that is org-scoped for the staff branch only.
     * Self-access is always allowed, unconditionally, exactly as before.
     *
     * <p>Same fail-open safety property as the other guards narrowed this session — see {@code
     * LearningService.isAuthorizedStaff} for the full reasoning. A student record resolves to an
     * org unit through its programme's department, the same one-hop chain {@code DocumentService}
     * uses for a document owner.
     */
    private void requireSelfOrAuthorizedStaff(CurrentUser caller, Student student) {
        if (caller.userId().equals(student.getUserId())) {
            return;
        }
        if (!caller.isStaff()) {
            throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return;
        }
        if (staffAppointments.activeAppointmentsOf(caller.userId()).isEmpty()) {
            return;
        }
        Optional<UUID> orgUnitId = academicStructure
                .departmentOfProgramme(student.getProgrammeId())
                .flatMap(departmentId -> staffAppointments.orgUnitFor("DEPARTMENT", departmentId));
        if (orgUnitId.isPresent() && !staffAppointments.isAppointedOver(caller.userId(), orgUnitId.get())) {
            throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
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
        String officeHours = advisorOfficeHoursRepository
                .findByAdvisorUserId(student.getAdvisorUserId())
                .map(AdvisorOfficeHours::getOfficeHours)
                .orElse(null);
        return StudentResponse.from(
                student,
                advisor.map(UserDirectory.UserSummary::fullName).orElse(null),
                advisor.map(UserDirectory.UserSummary::email).orElse(null),
                officeHours);
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
            student.assignAdvisor(request.advisorUserId());
        }
        if (request.contact() != null) {
            applyContact(student, request.contact());
        }
        if (request.residencyClassification() != null) {
            student.reclassifyResidency(request.residencyClassification());
        }

        return toResponse(student);
    }

    /** Registry correction of contact details on a student record. */
    @Transactional
    public void updateContactById(UUID studentId, UpdateOwnProfileRequest request) {
        applyContact(require(studentId), request);
    }

    /** Every open programme membership — the primary major alongside any minors or double majors. */
    public List<ProgrammeMembershipResponse> listProgrammeMemberships(UUID studentId) {
        require(studentId);
        return programmeEnrolmentService.openMembershipsOf(studentId).stream()
                .map(ProgrammeMembershipResponse::from)
                .toList();
    }

    /** Adds a minor, specialisation, or second major — never the primary membership. */
    @Transactional
    public ProgrammeMembershipResponse addProgrammeMembership(UUID studentId, AddProgrammeMembershipRequest request) {
        require(studentId);
        if (!academicStructure.programmeExists(request.programmeId())) {
            throw new ResourceNotFoundException(
                    StudentErrorCode.STUDENT_PROGRAMME_NOT_FOUND,
                    "No programme exists with id " + request.programmeId());
        }
        return ProgrammeMembershipResponse.from(programmeEnrolmentService.addSecondary(
                studentId, request.programmeId(), request.kind(), request.startedOn()));
    }

    /** Ends one secondary programme membership. The primary membership can only be ended by transfer. */
    @Transactional
    public void endProgrammeMembership(UUID studentId, UUID membershipId, EndProgrammeMembershipRequest request) {
        require(studentId);
        UUID approvedBy = currentUserProvider.find().map(CurrentUser::userId).orElse(null);
        programmeEnrolmentService.endSecondary(
                membershipId, request.endedOn(), request.endReason(), request.reason(), approvedBy);
    }

    /** Office hours the caller has posted, stored once per advisor. */
    public AdvisorOfficeHoursResponse findOwnAdvisorOfficeHours() {
        CurrentUser caller = requireAdvisor();
        return advisorOfficeHoursRepository
                .findByAdvisorUserId(caller.userId())
                .map(AdvisorOfficeHours::getOfficeHours)
                .map(AdvisorOfficeHoursResponse::new)
                .orElseGet(() -> new AdvisorOfficeHoursResponse(null));
    }

    /** Updates the office hours the caller has posted, once, regardless of how many advisees they have. */
    @Transactional
    public AdvisorOfficeHoursResponse updateOwnAdvisorOfficeHours(String officeHours) {
        CurrentUser caller = requireAdvisor();
        String normalized = blankToNull(officeHours);
        AdvisorOfficeHours row = advisorOfficeHoursRepository
                .findByAdvisorUserId(caller.userId())
                .orElseGet(() -> new AdvisorOfficeHours(caller.userId(), normalized));
        row.replace(normalized);
        advisorOfficeHoursRepository.save(row);
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

    /**
     * G8 (partial): advising had no depth beyond assignment and office hours — no way to record
     * what was discussed with an advisee, or for the next advisor to read it if the assignment
     * changes. Visible only to the student's current advisor and registry staff, not broadcast to
     * every staff role, since a note may say something the student does not know staff can see.
     */
    @Transactional
    public AdvisingNoteResponse addAdvisingNote(UUID studentId, CreateAdvisingNoteRequest request) {
        Student student = require(studentId);
        CurrentUser caller = requireAssignedAdvisorOrRegistry(student);
        AdvisingNote saved = advisingNoteRepository.save(new AdvisingNote(studentId, caller.userId(), request.note()));
        return AdvisingNoteResponse.from(saved, caller.fullName());
    }

    public List<AdvisingNoteResponse> listAdvisingNotes(UUID studentId) {
        Student student = require(studentId);
        requireAssignedAdvisorOrRegistry(student);
        return advisingNoteRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(note -> AdvisingNoteResponse.from(
                        note,
                        userDirectory.findById(note.getAdvisorUserId())
                                .map(UserDirectory.UserSummary::fullName)
                                .orElse(null)))
                .toList();
    }

    /**
     * G8: advising had notes but no way to actually schedule a meeting with an advisee, or for the
     * student to see one coming up. Advisor-initiated, the same direction {@link #addAdvisingNote}
     * already takes.
     */
    @Auditable(
            action = AuditTrail.Action.ADVISING_APPOINTMENT_SCHEDULED,
            entityType = AuditTrail.EntityType.STUDENT,
            entityId = "#studentId")
    @Transactional
    public AdvisingAppointmentResponse scheduleAdvisingAppointment(
            UUID studentId, CreateAdvisingAppointmentRequest request) {
        Student student = require(studentId);
        CurrentUser caller = requireAssignedAdvisorOrRegistry(student);
        AdvisingAppointment saved = advisingAppointmentRepository.save(new AdvisingAppointment(
                studentId, caller.userId(), request.scheduledAt(), request.durationMinutes(), request.note()));
        return AdvisingAppointmentResponse.from(saved, caller.fullName());
    }

    public List<AdvisingAppointmentResponse> listAdvisingAppointments(UUID studentId) {
        Student student = require(studentId);
        requireAssignedAdvisorOrRegistry(student);
        return advisingAppointmentRepository.findByStudentIdOrderByScheduledAtDesc(studentId).stream()
                .map(appointment -> AdvisingAppointmentResponse.from(
                        appointment,
                        userDirectory
                                .findById(appointment.getAdvisorUserId())
                                .map(UserDirectory.UserSummary::fullName)
                                .orElse(null)))
                .toList();
    }

    /** The caller's own upcoming and past appointments — self-service, no advisor check needed. */
    public List<AdvisingAppointmentResponse> listOwnAdvisingAppointments() {
        UUID userId = currentUserProvider.require().userId();
        Student student = studentRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "You do not have a student record"));
        return advisingAppointmentRepository.findByStudentIdOrderByScheduledAtDesc(student.getId()).stream()
                .map(appointment -> AdvisingAppointmentResponse.from(
                        appointment,
                        userDirectory
                                .findById(appointment.getAdvisorUserId())
                                .map(UserDirectory.UserSummary::fullName)
                                .orElse(null)))
                .toList();
    }

    @Auditable(
            action = AuditTrail.Action.ADVISING_APPOINTMENT_CANCELLED,
            entityType = AuditTrail.EntityType.STUDENT,
            entityId = "#result.studentId()")
    @Transactional
    public AdvisingAppointmentResponse cancelAdvisingAppointment(
            UUID appointmentId, CancelAdvisingAppointmentRequest request) {
        AdvisingAppointment appointment = advisingAppointmentRepository
                .findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        StudentErrorCode.ADVISING_APPOINTMENT_NOT_FOUND,
                        "No advising appointment exists with id " + appointmentId));
        Student student = require(appointment.getStudentId());
        CurrentUser caller = requireAssignedAdvisorOrRegistry(student);
        appointment.cancel(request.reason());
        return AdvisingAppointmentResponse.from(appointment, caller.fullName());
    }

    private CurrentUser requireAssignedAdvisorOrRegistry(Student student) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return caller;
        }
        if (caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR) && caller.userId().equals(student.getAdvisorUserId())) {
            return caller;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "Only this student's advisor or the registry may view advising notes");
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

    /**
     * Applies a system-derived academic-standing status change — the entry point {@code
     * DefaultStudentLifecycle.applyAcademicStanding} calls for the ACTIVE/PROBATION outcomes {@code
     * TermCloseService} drives automatically at term close. Reuses the same transition-validity,
     * mandatory-reason and audit path as the direct staff-facing status change: a system-derived
     * change deserves exactly the same record as a human-entered one, and refusing an illegal
     * transition here rather than forcing it is what keeps this safe to call unconditionally.
     */
    @Transactional
    public void applyAcademicStanding(UUID studentId, StudentStatus target, String reason) {
        applyStatusChange(require(studentId), target, reason);
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
                "\"" + current.name() + "\"",
                "\"" + target.name() + "\"");
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
