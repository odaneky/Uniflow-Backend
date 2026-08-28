package com.university.lms.request.repository;

import com.university.lms.request.domain.ServiceRequestAttachment;
import com.university.lms.request.domain.ServiceRequestAttachment.ServiceRequestAttachmentId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestAttachmentRepository
        extends JpaRepository<ServiceRequestAttachment, ServiceRequestAttachmentId> {

    List<ServiceRequestAttachment> findByRequestIdOrderByUploadedAtAsc(UUID requestId);

    boolean existsByRequestIdAndDocumentId(UUID requestId, UUID documentId);
}
