package com.university.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.administration.domain.AuditEvent;
import com.university.lms.administration.repository.AuditEventRepository;
import com.university.lms.finance.dto.CreateFeeRequest;
import com.university.lms.finance.dto.FeeResponse;
import com.university.lms.finance.domain.FeeAssessment;
import com.university.lms.finance.domain.FeeKind;
import com.university.lms.finance.service.FeeCatalogService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.RunAs;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * B3: proves {@code @Auditable} actually works end to end — real Spring context, real AOP proxy,
 * real Postgres row — not just that {@link com.university.lms.administration.service.AuditableAspect}
 * evaluates SpEL correctly in isolation (see {@code AuditableAspectTest} for that). A plain unit
 * test constructing {@code FeeCatalogService} directly would never go through the proxy the
 * annotation depends on, so this is the only test that can catch a wiring mistake — a missing
 * {@code @EnableAspectJAutoProxy}, a bean not actually proxied, {@code spring-boot-starter-aop} not
 * on the classpath.
 */
class FinanceAuditIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private FeeCatalogService feeCatalogService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    @DisplayName("creating a fee through the real proxied bean writes an audit event")
    void creatingAFeeWritesAnAuditEvent() throws Exception {
        FeeResponse created = RunAs.staff(() -> feeCatalogService.create(new CreateFeeRequest(
                "B3 Audit Test Fee " + System.nanoTime(),
                "Exercises the Auditable aspect",
                new BigDecimal("42.00"),
                FeeKind.MANDATORY,
                FeeAssessment.ONCE_PER_TERM,
                null,
                null)));

        List<AuditEvent> events = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        "Fee", created.id(), org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getAction()).isEqualTo("FEE_CREATED");
        assertThat(event.getDetails()).isEqualTo(created.name());
        assertThat(event.getActorLabel()).isNotBlank();
    }
}
