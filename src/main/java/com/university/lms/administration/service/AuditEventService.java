package com.university.lms.administration.service;

import com.university.lms.administration.dto.AuditEventResponse;
import com.university.lms.administration.repository.AuditEventRepository;
import com.university.lms.common.dto.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit trail. Writes go through {@code AuditTrail}, never through here. */
@Service
@Transactional(readOnly = true)
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public AuditEventService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public PageResponse<AuditEventResponse> search(
            String action, String entityType, UUID actorUserId, Pageable pageable) {
        return PageResponse.from(
                auditEventRepository.search(blankToNull(action), blankToNull(entityType), actorUserId, pageable),
                AuditEventResponse::from);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
