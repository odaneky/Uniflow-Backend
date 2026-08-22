package com.university.lms.administration.web;

import com.university.lms.administration.dto.AuditEventResponse;
import com.university.lms.administration.service.AuditEventService;
import com.university.lms.common.dto.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator view of the append-only trail.
 *
 * <p>Authorization is in {@code SecurityConfig}: SYSTEM_ADMIN and REGISTRAR only. The catch-all
 * authenticated GET rule would otherwise let every student enumerate it.
 */
@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {

    private final AuditEventService auditEventService;

    public AuditEventController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID actorUserId,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditEventService.search(action, entityType, actorUserId, pageable);
    }
}
