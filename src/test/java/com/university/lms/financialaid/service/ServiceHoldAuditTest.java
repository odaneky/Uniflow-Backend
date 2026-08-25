package com.university.lms.financialaid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.service.AuditableAspect;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.financialaid.domain.HoldType;
import com.university.lms.financialaid.domain.ServiceHold;
import com.university.lms.financialaid.dto.ServiceHoldResponse;
import com.university.lms.financialaid.repository.ServiceHoldRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * B3: pins the SpEL in {@code ServiceHoldService}'s {@code @Auditable} annotations specifically —
 * {@code AuditableAspectTest} already proves the aspect's own mechanics, so this exists because
 * {@code #result.id()} (a record) and {@code #result.getId()} (a JavaBean-style entity, since
 * {@code placeHoldInternal} returns the domain {@code ServiceHold} rather than its response DTO)
 * are two different resolution paths and a typo in either would only show up here, not in any
 * generic test of the aspect. Wraps the real service in a genuine AspectJ proxy, same as
 * {@code AuditableAspectTest}, rather than a full Postgres integration test — the DI/proxy wiring
 * itself is already proven end to end by {@code FinanceAuditIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class ServiceHoldAuditTest {

    @Mock
    private ServiceHoldRepository repository;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AuditTrail auditTrail;

    private ServiceHoldService proxy;

    private static final UUID STUDENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ServiceHoldService target = new ServiceHoldService(repository, studentDirectory, currentUserProvider);
        AuditableAspect aspect = new AuditableAspect(auditTrail, currentUserProvider);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        proxy = factory.getProxy();

        lenient().when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        lenient().when(repository.save(any(ServiceHold.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CurrentUser registrar() {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "registrar", "registrar@example.edu", "Rita Registrar",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
    }

    @Test
    @DisplayName("placeHold: entityId comes from #result.id() on the response record")
    void placeHoldResolvesEntityIdFromTheResponseRecord() {
        when(currentUserProvider.require()).thenReturn(registrar());

        ServiceHoldResponse response = proxy.placeHold(STUDENT_ID, HoldType.MANUAL, "unpaid balance");

        verify(auditTrail).record(
                any(), eq("Rita Registrar"), eq(AuditTrail.Action.SERVICE_HOLD_PLACED),
                eq(AuditTrail.EntityType.SERVICE_HOLD), eq(response.id()), eq("MANUAL: unpaid balance"));
    }

    @Test
    @DisplayName("clearHold: entityId comes directly from the #holdId parameter")
    void clearHoldResolvesEntityIdFromTheParameter() {
        when(currentUserProvider.require()).thenReturn(registrar());
        UUID holdId = UUID.randomUUID();
        ServiceHold existing = new ServiceHold(STUDENT_ID, HoldType.MANUAL, "unpaid balance", Instant.now(), null);
        when(repository.findById(holdId)).thenReturn(Optional.of(existing));

        proxy.clearHold(holdId);

        verify(auditTrail).record(
                any(), any(), eq(AuditTrail.Action.SERVICE_HOLD_CLEARED),
                eq(AuditTrail.EntityType.SERVICE_HOLD), eq(holdId), any());
    }

    @Test
    @DisplayName("clearSapHold: scoped to the student, since it may clear more than one hold")
    void clearSapHoldIsScopedToTheStudent() {
        when(currentUserProvider.require()).thenReturn(registrar());
        when(repository.findByStudentIdAndActiveTrueOrderByPlacedAtDesc(STUDENT_ID)).thenReturn(java.util.List.of());

        proxy.clearSapHold(STUDENT_ID);

        verify(auditTrail).record(
                any(), any(), eq(AuditTrail.Action.SERVICE_HOLD_CLEARED),
                eq(AuditTrail.EntityType.STUDENT), eq(STUDENT_ID), any());
    }
}
