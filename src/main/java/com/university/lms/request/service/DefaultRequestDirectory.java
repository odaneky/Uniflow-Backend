package com.university.lms.request.service;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.request.api.RequestDirectory;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.repository.ServiceRequestRepository;
import com.university.lms.student.api.StudentDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultRequestDirectory implements RequestDirectory {

    private final ServiceRequestRepository repository;
    private final StudentDirectory studentDirectory;

    public DefaultRequestDirectory(ServiceRequestRepository repository, StudentDirectory studentDirectory) {
        this.repository = repository;
        this.studentDirectory = studentDirectory;
    }

    @Override
    public Optional<RequestSummary> findById(UUID requestId) {
        return repository.findById(requestId).map(this::toSummary);
    }

    @Override
    public Optional<UUID> studentUserIdOf(UUID studentId) {
        return studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::userId);
    }

    @Override
    public Optional<String> additionalNotificationRole(ServiceRequestType type) {
        return type == ServiceRequestType.SAP_APPEAL
                ? Optional.of(SecurityRoles.FINANCIAL_AID_OFFICER)
                : Optional.empty();
    }

    private RequestSummary toSummary(ServiceRequest request) {
        UUID studentUserId =
                studentDirectory.findById(request.getStudentId()).map(StudentDirectory.StudentSummary::userId).orElse(null);
        return new RequestSummary(
                request.getId(),
                request.getStudentId(),
                studentUserId,
                request.getRequestType(),
                request.getStatus(),
                request.getReference(),
                request.getAssignedTo(),
                request.getDeliverableDocumentId(),
                request.getUpdatedAt());
    }
}
