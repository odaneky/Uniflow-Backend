package com.university.lms.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DomainOutboxRepository extends JpaRepository<DomainOutbox, UUID> {

    @Query(
            value =
                    """
                    SELECT * FROM domain_outbox
                    WHERE status IN ('PENDING', 'FAILED')
                      AND next_attempt_at <= :now
                    ORDER BY created_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<DomainOutbox> claimBatch(@Param("now") Instant now, @Param("limit") int limit);
}
