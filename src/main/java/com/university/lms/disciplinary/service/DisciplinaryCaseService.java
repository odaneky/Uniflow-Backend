package com.university.lms.disciplinary.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.disciplinary.domain.DisciplinaryCase;
import com.university.lms.disciplinary.domain.DisciplinaryErrorCode;
import com.university.lms.disciplinary.dto.AssignCaseOfficerRequest;
import com.university.lms.disciplinary.dto.CreateDisciplinaryCaseNoteRequest;
import com.university.lms.disciplinary.dto.CreateDisciplinaryCaseRequest;
import com.university.lms.disciplinary.dto.DisciplinaryCaseNoteResponse;
import com.university.lms.disciplinary.dto.DisciplinaryCaseResponse;
import com.university.lms.disciplinary.dto.ResolveDisciplinaryCaseRequest;
import com.university.lms.disciplinary.domain.DisciplinaryCaseNote;
import com.university.lms.disciplinary.repository.DisciplinaryCaseNoteRepository;
import com.university.lms.disciplinary.repository.DisciplinaryCaseRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.StudentDirectory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * G7: disciplinary cases, deliberately kept out of the binary staff/student model the rest of this
 * codebase uses — confidentiality here is per-case. Filing is open to any staff member (a lecturer
 * reporting what they witnessed); reading is not. Only the registry and whichever staff member is
 * assigned to a given case may see it once filed, which is why every read here goes through {@link
 * #requireReadAccess} rather than the {@code STAFF_ONLY} the controller's {@code @AccessClass}
 * promises — that annotation is the coarse layer; this is the case-level one underneath it.
 */
@Service
public class DisciplinaryCaseService {

    private final DisciplinaryCaseRepository caseRepository;
    private final DisciplinaryCaseNoteRepository noteRepository;
    private final CurrentUserProvider currentUserProvider;
    private final StudentDirectory studentDirectory;
    private final UserDirectory userDirectory;
    private final RecordAccessLog recordAccessLog;

    public DisciplinaryCaseService(
            DisciplinaryCaseRepository caseRepository,
            DisciplinaryCaseNoteRepository noteRepository,
            CurrentUserProvider currentUserProvider,
            StudentDirectory studentDirectory,
            UserDirectory userDirectory,
            RecordAccessLog recordAccessLog) {
        this.caseRepository = caseRepository;
        this.noteRepository = noteRepository;
        this.currentUserProvider = currentUserProvider;
        this.studentDirectory = studentDirectory;
        this.userDirectory = userDirectory;
        this.recordAccessLog = recordAccessLog;
    }

    @Auditable(
            action = AuditTrail.Action.DISCIPLINARY_CASE_FILED,
            entityType = AuditTrail.EntityType.DISCIPLINARY_CASE,
            entityId = "#result.id()")
    @Transactional
    public DisciplinaryCaseResponse fileCase(CreateDisciplinaryCaseRequest request) {
        if (!studentDirectory.exists(request.studentId())) {
            throw new ResourceNotFoundException(
                    DisciplinaryErrorCode.CASE_NOT_FOUND, "No student exists with id " + request.studentId());
        }
        CurrentUser caller = currentUserProvider.require();
        DisciplinaryCase saved = caseRepository.save(new DisciplinaryCase(
                nextCaseNumber(), request.studentId(), request.category(), request.summary(), caller.userId()));
        return toResponse(saved);
    }

    @Auditable(
            action = AuditTrail.Action.DISCIPLINARY_CASE_OFFICER_ASSIGNED,
            entityType = AuditTrail.EntityType.DISCIPLINARY_CASE,
            entityId = "#caseId")
    @Transactional
    public DisciplinaryCaseResponse assignOfficer(UUID caseId, AssignCaseOfficerRequest request) {
        requireRegistry();
        DisciplinaryCase disciplinaryCase = require(caseId);
        disciplinaryCase.assignOfficer(request.officerUserId());
        return toResponse(caseRepository.save(disciplinaryCase));
    }

    @Auditable(
            action = AuditTrail.Action.DISCIPLINARY_CASE_CLOSED,
            entityType = AuditTrail.EntityType.DISCIPLINARY_CASE,
            entityId = "#caseId")
    @Transactional
    public DisciplinaryCaseResponse close(UUID caseId, ResolveDisciplinaryCaseRequest request) {
        DisciplinaryCase disciplinaryCase = require(caseId);
        requireDecisionAuthority(disciplinaryCase);
        disciplinaryCase.close(request.status(), request.outcome(), request.reason());
        return toResponse(caseRepository.save(disciplinaryCase));
    }

    @Auditable(
            action = AuditTrail.Action.DISCIPLINARY_CASE_NOTE_ADDED,
            entityType = AuditTrail.EntityType.DISCIPLINARY_CASE,
            entityId = "#caseId")
    @Transactional
    public DisciplinaryCaseNoteResponse addNote(UUID caseId, CreateDisciplinaryCaseNoteRequest request) {
        DisciplinaryCase disciplinaryCase = require(caseId);
        CurrentUser caller = requireReadAccess(disciplinaryCase);
        DisciplinaryCaseNote saved = noteRepository.save(new DisciplinaryCaseNote(caseId, caller.userId(), request.note()));
        return DisciplinaryCaseNoteResponse.from(saved, caller.fullName());
    }

    public DisciplinaryCaseResponse find(UUID caseId) {
        DisciplinaryCase disciplinaryCase = require(caseId);
        CurrentUser caller = requireReadAccess(disciplinaryCase);
        recordAccessLog.record(
                caller.userId(),
                caller.fullName(),
                disciplinaryCase.getStudentId(),
                RecordAccessLog.RecordType.DISCIPLINARY,
                RecordAccessLog.Action.VIEW,
                "Case " + disciplinaryCase.getCaseNumber());
        return toResponse(disciplinaryCase);
    }

    public List<DisciplinaryCaseNoteResponse> listNotes(UUID caseId) {
        DisciplinaryCase disciplinaryCase = require(caseId);
        CurrentUser caller = requireReadAccess(disciplinaryCase);
        recordAccessLog.record(
                caller.userId(),
                caller.fullName(),
                disciplinaryCase.getStudentId(),
                RecordAccessLog.RecordType.DISCIPLINARY,
                RecordAccessLog.Action.VIEW,
                "Case " + disciplinaryCase.getCaseNumber() + " notes");
        return noteRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
                .map(note -> DisciplinaryCaseNoteResponse.from(note, nameOf(note.getAuthorUserId())))
                .toList();
    }

    /** Only the cases this caller may actually read — the registry sees all of a student's, everyone else only their own. */
    public List<DisciplinaryCaseResponse> listForStudent(UUID studentId) {
        CurrentUser caller = currentUserProvider.require();
        boolean registry = caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR);
        return caseRepository.findByStudentIdOrderByFiledAtDesc(studentId).stream()
                .filter(row -> registry || row.isReadableBy(caller.userId()))
                .map(this::toResponse)
                .toList();
    }

    private CurrentUser requireReadAccess(DisciplinaryCase disciplinaryCase) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return caller;
        }
        if (disciplinaryCase.isReadableBy(caller.userId())) {
            return caller;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "Only the registry or this case's filer or assigned officer may view it");
    }

    private CurrentUser requireDecisionAuthority(DisciplinaryCase disciplinaryCase) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return caller;
        }
        if (caller.userId().equals(disciplinaryCase.getAssignedOfficerUserId())) {
            return caller;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "Only the registry or this case's assigned officer may decide it");
    }

    private CurrentUser requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return caller;
        }
        throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "Only the registry may assign a case officer");
    }

    private DisciplinaryCase require(UUID caseId) {
        return caseRepository
                .findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        DisciplinaryErrorCode.CASE_NOT_FOUND, "No disciplinary case exists with id " + caseId));
    }

    private String nextCaseNumber() {
        for (int i = 0; i < 8; i++) {
            String candidate =
                    "DC-" + String.format("%05d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000));
            if (!caseRepository.existsByCaseNumber(candidate)) {
                return candidate;
            }
        }
        return "DC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String nameOf(UUID userId) {
        return userDirectory.findById(userId).map(UserDirectory.UserSummary::fullName).orElse(null);
    }

    private DisciplinaryCaseResponse toResponse(DisciplinaryCase disciplinaryCase) {
        return DisciplinaryCaseResponse.from(
                disciplinaryCase,
                nameOf(disciplinaryCase.getFiledByUserId()),
                disciplinaryCase.getAssignedOfficerUserId() == null
                        ? null
                        : nameOf(disciplinaryCase.getAssignedOfficerUserId()));
    }
}
