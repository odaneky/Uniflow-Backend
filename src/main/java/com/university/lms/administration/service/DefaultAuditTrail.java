package com.university.lms.administration.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.domain.AuditEvent;
import com.university.lms.administration.repository.AuditEventRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail in its own transaction.
 *
 * <p>{@code REQUIRES_NEW} because the most valuable audit records describe operations that were
 * <em>refused</em>. A refused identity link throws, rolling the caller's transaction back; joining
 * that transaction would roll the evidence back with it, and the one event an investigator most
 * wants would be the one event that is never written.
 *
 * <p>An audit failure never fails the operation being audited. That is a deliberate trade: this
 * system's records are academic rather than financial, and refusing to serve a student because a
 * log write failed is the worse outcome. It is logged at ERROR so the gap is visible — if a
 * regulatory regime later requires the opposite, this is the single method to change.
 */
@Service
public class DefaultAuditTrail implements AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuditTrail.class);

    /** Matches the column width; a truncated record beats a failed insert. */
    private static final int MAX_DETAILS = 4000;

    private static final int MAX_ACTOR_LABEL = 200;

    private final AuditEventRepository auditEventRepository;

    public DefaultAuditTrail(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID actorUserId, String actorLabel, String action, String entityType, UUID entityId, String details) {
        try {
            auditEventRepository.save(new AuditEvent(
                    actorUserId,
                    truncate(actorLabel, MAX_ACTOR_LABEL),
                    action,
                    entityType,
                    entityId,
                    truncate(details, MAX_DETAILS)));
        } catch (RuntimeException ex) {
            log.error("Failed to write audit event {} for {} {}", action, entityType, entityId, ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
