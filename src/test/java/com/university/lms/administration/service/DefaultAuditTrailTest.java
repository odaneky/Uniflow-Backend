package com.university.lms.administration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.administration.domain.AuditEvent;
import com.university.lms.administration.repository.AuditEventRepository;
import com.university.lms.common.ratelimit.RateLimitProperties;
import com.university.lms.common.web.ClientIpResolver;
import com.university.lms.common.web.CorrelationIdFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@code source_ip} and {@code correlation_id} must be populated on every write, including one made
 * through the shorter, pre-existing {@code record(...)} overloads — that is the whole point of
 * resolving them here rather than asking ~90 existing call sites to start passing them. {@code
 * reason}/{@code beforeValue}/{@code afterValue} are the opposite: they stay null unless a caller
 * explicitly supplies them through the new full-form overload.
 */
@ExtendWith(MockitoExtension.class)
class DefaultAuditTrailTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private DefaultAuditTrail auditTrail;

    @BeforeEach
    void setUp() {
        ClientIpResolver clientIpResolver = new ClientIpResolver(new RateLimitProperties(true, 1024, List.of(), List.of()));
        auditTrail = new DefaultAuditTrail(auditEventRepository, clientIpResolver);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void theShortOverloadStillLeavesReasonAndBeforeAfterNull() {
        auditTrail.record(UUID.randomUUID(), "GRADE_CHANGED", "Grade", UUID.randomUUID(), "some detail");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertThat(saved.getReason()).isNull();
        assertThat(saved.getBeforeValue()).isNull();
        assertThat(saved.getAfterValue()).isNull();
        assertThat(saved.getDetails()).isEqualTo("some detail");
    }

    @Test
    void theFullOverloadThreadsReasonAndBeforeAfterThrough() {
        UUID actor = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        auditTrail.record(
                actor,
                "Rita Registrar",
                "GRADE_CHANGED",
                "Grade",
                entityId,
                "COMP2140 · B",
                "Transcription error corrected on appeal",
                "{\"letter\":\"C\",\"percentage\":62.00}",
                "{\"letter\":\"B\",\"percentage\":75.00}");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertThat(saved.getReason()).isEqualTo("Transcription error corrected on appeal");
        assertThat(saved.getBeforeValue()).isEqualTo("{\"letter\":\"C\",\"percentage\":62.00}");
        assertThat(saved.getAfterValue()).isEqualTo("{\"letter\":\"B\",\"percentage\":75.00}");
    }

    @Test
    void sourceIpAndCorrelationIdAreResolvedFromTheCurrentRequestEvenOnTheShortOverload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("correlationId", "test-correlation-id");

        auditTrail.record(UUID.randomUUID(), "ROLE_GRANTED", "User", UUID.randomUUID(), "granted REGISTRAR");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertThat(saved.getSourceIp()).isEqualTo("203.0.113.7");
        assertThat(saved.getCorrelationId()).isNotBlank();
    }

    @Test
    void sourceIpIsNullOutsideARequest() {
        // No RequestContextHolder attributes set — the scheduled-job / outbox-dispatcher case.
        auditTrail.record(UUID.randomUUID(), "IDENTITY_PROVISIONED", "User", UUID.randomUUID(), "first login");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceIp()).isNull();
    }

    @Test
    void aCallerCannotForgeItsOwnSourceIpOrCorrelationId() {
        // The interface has no parameter for either — this exercises the full overload and checks
        // that nothing about it lets a caller pass one in; the assertion is really about the
        // signature, but running it end to end also confirms resolution wins over "nothing set".
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditTrail.record(
                UUID.randomUUID(), "Actor", "GRADE_CHANGED", "Grade", UUID.randomUUID(), "detail",
                "reason", null, null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceIp()).isEqualTo("198.51.100.9");
    }

    @Test
    void aFailedWriteIsLoggedAndNeverPropagatedToTheCaller() {
        when(auditEventRepository.save(any())).thenThrow(new RuntimeException("db is down"));

        auditTrail.record(UUID.randomUUID(), "GRADE_CHANGED", "Grade", UUID.randomUUID(), "detail");
        // No exception reaching here is the assertion: an audit failure must never fail the
        // operation being audited.
    }
}
