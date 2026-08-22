package com.university.lms.student.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.domain.IdentityErrorCode;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.identity.spi.IdentityAccount;
import com.university.lms.identity.spi.IdentityProvider;
import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentErrorCode;
import com.university.lms.student.dto.ProvisionStudentRequest;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.repository.StudentRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Academic provisioning — the second half of getting a student into the system.
 *
 * <p>There are two provisioning concepts and they are not the same:
 *
 * <ul>
 *   <li><b>Authentication provisioning</b> creates the identity. It happens upstream, in the
 *       university's registration system, and lands in the identity provider.
 *   <li><b>Academic provisioning</b> — this — attaches a programme, an admission date and an
 *       academic status to an identity that already exists.
 * </ul>
 *
 * <p>The two are correlated by <b>student number</b>, the identifier the university actually owns.
 * That is why this takes a student number and not a local id: the person may never have signed in
 * here, and requiring them to would mean a registrar could not enrol anybody until they had.
 *
 * <p>This service <b>refuses to invent an identity</b>. If the student number is unknown to the
 * identity provider, the request fails. Creating a local record for someone the university has not
 * actually registered is exactly how a system acquires a second, unauthoritative roster.
 */
@Service
public class StudentProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(StudentProvisioningService.class);

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final IdentityProvider identityProvider;
    private final AuditTrail auditTrail;
    private final CurrentUserProvider currentUserProvider;

    public StudentProvisioningService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            IdentityProvider identityProvider,
            AuditTrail auditTrail,
            CurrentUserProvider currentUserProvider) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.identityProvider = identityProvider;
        this.auditTrail = auditTrail;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public StudentResponse provision(ProvisionStudentRequest request) {
        String studentNumber = request.studentNumber().trim();

        if (studentRepository.existsByStudentNumber(studentNumber)) {
            throw new ResourceAlreadyExistsException(
                    StudentErrorCode.STUDENT_NUMBER_ALREADY_EXISTS,
                    "A student record already exists for " + studentNumber);
        }
        if (!identityProvider.isAvailable()) {
            throw new BusinessException(
                    IdentityErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,
                    "Identity-provider administration is not configured, so the student number cannot be verified");
        }

        // The institutional identity must already exist. Looked up by username because that is what
        // a student signs in with; the lookup is exact, never a prefix match.
        IdentityAccount account = identityProvider
                .findByUsername(studentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        IdentityErrorCode.USER_NOT_FOUND,
                        "No identity exists for student number " + studentNumber
                                + "; the student must be provisioned in the identity provider first"));

        User user = localProjectionOf(account);
        Student student = new Student(user.getId(), studentNumber, request.programmeId(), request.admissionDate());
        if (request.expectedGraduationDate() != null) {
            student.expectGraduationOn(request.expectedGraduationDate());
        }

        try {
            Student saved = studentRepository.saveAndFlush(student);
            auditTrail.record(
                    actorId(),
                    AuditTrail.Action.STUDENT_PROVISIONED,
                    "Student",
                    saved.getId(),
                    "Academic record provisioned for student number " + studentNumber);
            log.info("Provisioned student record {} for student number {}", saved.getId(), studentNumber);
            return StudentResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // The unique indexes are the guarantee under concurrency; the checks above only produce
            // a better message on the common path.
            throw new ResourceAlreadyExistsException(
                    StudentErrorCode.STUDENT_NUMBER_ALREADY_EXISTS,
                    "A student record already exists for " + studentNumber,
                    ex);
        }
    }

    /**
     * The local row for an identity, created if this is the first time we have seen it.
     *
     * <p>Same shape as just-in-time provisioning at login, arrived at from the other direction. Both
     * paths converge on one row keyed by the provider's subject, and the unique index on that column
     * is what guarantees they cannot produce two.
     */
    private User localProjectionOf(IdentityAccount account) {
        return userRepository.findByKeycloakSubject(account.subject()).orElseGet(() -> {
            User created = userRepository.saveAndFlush(User.fromIdentityProvider(
                    account.subject(),
                    account.username(),
                    account.email(),
                    account.firstName() != null ? account.firstName() : "Unknown",
                    account.lastName() != null ? account.lastName() : "User"));
            auditTrail.record(
                    actorId(),
                    AuditTrail.Action.IDENTITY_LINKED,
                    "User",
                    created.getId(),
                    "Local projection created during academic provisioning");
            return created;
        });
    }

    private UUID actorId() {
        return currentUserProvider.find().map(CurrentUser::userId).orElse(null);
    }
}
