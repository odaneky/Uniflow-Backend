package com.university.lms.administration.repository;

import com.university.lms.administration.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Internal to the administration module. Append-only: no update or delete is exposed. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, UUID entityId, Pageable pageable);

    Page<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(UUID actorUserId, Pageable pageable);

    /**
     * Optional filters. UUID parameters sit next to {@code actorUserId} so Hibernate types them;
     * an untyped {@code :id is null} against PostgreSQL becomes a bytea comparison and fails.
     */
    @Query(
            """
            select e from AuditEvent e
            where (:action is null or e.action = :action)
              and (:entityType is null or e.entityType = :entityType)
              and (:actorUserId is null or e.actorUserId = :actorUserId)
            """)
    Page<AuditEvent> search(String action, String entityType, UUID actorUserId, Pageable pageable);
}
