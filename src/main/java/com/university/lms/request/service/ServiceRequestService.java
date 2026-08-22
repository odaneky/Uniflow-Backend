package com.university.lms.request.service;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.dto.CreateServiceRequestRequest;
import com.university.lms.request.dto.DecideServiceRequestRequest;
import com.university.lms.request.dto.ServiceRequestResponse;
import com.university.lms.request.repository.ServiceRequestRepository;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceRequestService {

    private static final Set<ServiceRequestStatus> CLOSED =
            EnumSet.of(ServiceRequestStatus.COMPLETED, ServiceRequestStatus.DENIED);

    private final ServiceRequestRepository requestRepository;
    private final StudentDirectory studentDirectory;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;

    public ServiceRequestService(
            ServiceRequestRepository requestRepository,
            StudentDirectory studentDirectory,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider) {
        this.requestRepository = requestRepository;
        this.studentDirectory = studentDirectory;
        this.userDirectory = userDirectory;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ServiceRequestResponse> own() {
        return requestRepository.findByStudentIdOrderByUpdatedAtDesc(requireOwnStudent()).stream()
                .map(this::toResponse)
                .toList();
    }

    public ServiceRequestResponse ownById(UUID id) {
        UUID studentId = requireOwnStudent();
        ServiceRequest request = requestRepository
                .findById(id)
                .filter(row -> row.getStudentId().equals(studentId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        RequestErrorCode.REQUEST_NOT_FOUND, "No request exists with id " + id));
        return toResponse(request);
    }

    public PageResponse<ServiceRequestResponse> all(Pageable pageable) {
        requireRegistry();
        return PageResponse.from(requestRepository.findAll(pageable), this::toResponse);
    }

    @Transactional
    public ServiceRequestResponse createOwn(CreateServiceRequestRequest request) {
        UUID studentId = requireOwnStudent();
        if (requestRepository.existsByStudentIdAndRequestTypeAndStatusNotIn(studentId, request.type(), CLOSED)) {
            throw new ResourceAlreadyExistsException(
                    RequestErrorCode.REQUEST_ALREADY_OPEN,
                    "You already have an open " + request.type().displayName() + " request");
        }
        ServiceRequest saved = requestRepository.save(
                new ServiceRequest(studentId, request.type(), nextReference(request.type()), request.note()));
        return toResponse(saved);
    }

    @Transactional
    public ServiceRequestResponse decide(UUID id, DecideServiceRequestRequest decision) {
        CurrentUser caller = requireRegistry();
        if (decision.status() == ServiceRequestStatus.SUBMITTED) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_INVALID_DECISION, "A decision cannot return a request to submitted");
        }
        ServiceRequest request = requestRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        RequestErrorCode.REQUEST_NOT_FOUND, "No request exists with id " + id));
        if (request.getStatus().terminal()) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_ALREADY_DECIDED, "This request has already been closed");
        }
        request.decide(decision.status(), caller.userId(), decision.note(), Instant.now());
        return toResponse(request);
    }

    private ServiceRequestResponse toResponse(ServiceRequest request) {
        StudentDirectory.StudentSummary student = studentDirectory.findById(request.getStudentId()).orElse(null);
        String studentNumber = student == null ? null : student.studentNumber();
        String studentName = student == null
                ? null
                : userDirectory.findById(student.userId()).map(UserDirectory.UserSummary::fullName).orElse(null);
        String decidedByName = request.getDecidedBy() == null
                ? null
                : userDirectory.findById(request.getDecidedBy()).map(UserDirectory.UserSummary::fullName).orElse(null);
        return ServiceRequestResponse.from(request, studentNumber, studentName, decidedByName);
    }

    private String nextReference(ServiceRequestType type) {
        for (int i = 0; i < 8; i++) {
            String candidate = type.referencePrefix() + "-"
                    + String.format("%05d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000));
            if (!requestRepository.existsByReference(candidate)) {
                return candidate;
            }
        }
        return type.referencePrefix() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID requireOwnStudent() {
        CurrentUser caller = currentUserProvider.require();
        return studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
    }

    private CurrentUser requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        return caller;
    }
}
