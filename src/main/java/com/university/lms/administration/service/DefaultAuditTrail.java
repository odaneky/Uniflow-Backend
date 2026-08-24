package com.university.lms.administration.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.domain.AuditEvent;
import com.university.lms.administration.repository.AuditEventRepository;
import com.university.lms.common.web.ClientIpResolver;
import com.university.lms.common.web.CorrelationIdFilter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
 *
 * <p>{@code sourceIp} and {@code correlationId} are resolved here, from the current request, for
 * every write — including one that arrives through the older, shorter {@code record(...)} overloads
 * that this class does not directly implement. Neither is ever supplied by a caller: a value the
 * caller could set is a value that proves nothing about who actually made the request.
 */
@Service
public class DefaultAuditTrail implements AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuditTrail.class);

    /** Matches the column width; a truncated record beats a failed insert. */
    private static final int MAX_DETAILS = 4000;

    private static final int MAX_ACTOR_LABEL = 200;
    private static final int MAX_REASON = 1000;

    private final AuditEventRepository auditEventRepository;
    private final ClientIpResolver clientIpResolver;

    public DefaultAuditTrail(AuditEventRepository auditEventRepository, ClientIpResolver clientIpResolver) {
        this.auditEventRepository = auditEventRepository;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID actorUserId,
            String actorLabel,
            String action,
            String entityType,
            UUID entityId,
            String details,
            String reason,
            String beforeValue,
            String afterValue) {
        try {
            AuditEvent event = new AuditEvent(
                    actorUserId,
                    truncate(actorLabel, MAX_ACTOR_LABEL),
                    action,
                    entityType,
                    entityId,
                    truncate(details, MAX_DETAILS),
                    truncate(reason, MAX_REASON),
                    beforeValue,
                    afterValue);
            event.resolvedFrom(currentSourceIp(), CorrelationIdFilter.current());
            auditEventRepository.save(event);
        } catch (RuntimeException ex) {
            log.error("Failed to write audit event {} for {} {}", action, entityType, entityId, ex);
        }
    }

    /**
     * Null outside a request — a scheduled job or the outbox dispatcher has no client to attribute
     * the write to, and that is a fact worth recording as absence rather than a fabricated value.
     */
    private String currentSourceIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return clientIpResolver.resolve(servletAttributes.getRequest());
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
