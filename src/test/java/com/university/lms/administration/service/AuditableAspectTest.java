package com.university.lms.administration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
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
 * Exercises {@link AuditableAspect} through a real AspectJ proxy — a plain unit test constructing
 * the target directly would never go through the proxy at all, and so would never invoke the
 * advice, which is exactly the self-invocation caveat {@link Auditable}'s javadoc warns about.
 * {@link Target} stands in for a real {@code @Auditable}-annotated service.
 */
@ExtendWith(MockitoExtension.class)
class AuditableAspectTest {

    interface TargetApi {
        Created create(String name);

        void deactivate(UUID id);

        void replaceInstitutionWide();

        void explode();
    }

    record Created(UUID id, String name) {}

    static class Target implements TargetApi {
        @Override
        @Auditable(
                action = "THING_CREATED",
                entityType = "Thing",
                entityId = "#result.id()",
                details = "#result.name()")
        public Created create(String name) {
            return new Created(UUID.randomUUID(), name);
        }

        @Override
        @Auditable(action = "THING_DEACTIVATED", entityType = "Thing", entityId = "#id")
        public void deactivate(UUID id) {}

        @Override
        @Auditable(action = "THING_REPLACED", entityType = "Thing", entityId = "null")
        public void replaceInstitutionWide() {}

        @Override
        @Auditable(action = "THING_EXPLODED", entityType = "Thing", entityId = "null")
        public void explode() {
            throw new IllegalStateException("boom");
        }
    }

    @Mock
    private AuditTrail auditTrail;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private TargetApi proxy;

    @BeforeEach
    void setUp() {
        AuditableAspect aspect = new AuditableAspect(auditTrail, currentUserProvider);
        AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    private static CurrentUser caller() {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "caller", "caller@example.edu", "Rita Registrar",
                Optional.empty(), Set.of("REGISTRAR"), Set.of());
    }

    @Test
    @DisplayName("entityId and details are evaluated from #result, since a create-shaped method's id only exists on the response")
    void resolvesEntityIdAndDetailsFromResult() {
        when(currentUserProvider.require()).thenReturn(caller());

        Created created = proxy.create("Late Fee");

        verify(auditTrail).record(any(), eq("Rita Registrar"), eq("THING_CREATED"), eq("Thing"), eq(created.id()), eq("Late Fee"));
    }

    @Test
    @DisplayName("entityId is evaluated from a method parameter when there is no return value to read it from")
    void resolvesEntityIdFromAParameter() {
        when(currentUserProvider.require()).thenReturn(caller());
        UUID id = UUID.randomUUID();

        proxy.deactivate(id);

        verify(auditTrail).record(any(), eq("Rita Registrar"), eq("THING_DEACTIVATED"), eq("Thing"), eq(id), isNull());
    }

    @Test
    @DisplayName(
            "an audit failure (no authenticated caller resolvable) is swallowed, not thrown to the "
                    + "method's own caller — the write it is auditing must not roll back over this")
    void anAuditFailureDoesNotReachTheCaller() {
        when(currentUserProvider.require())
                .thenThrow(new com.university.lms.common.exception.UnauthorizedException(
                        com.university.lms.common.exception.CommonErrorCode.AUTHENTICATION_REQUIRED, "no token"));

        proxy.deactivate(UUID.randomUUID());

        verify(auditTrail, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an exception from the underlying method reaches the caller and no event is recorded")
    void doesNotAuditAMethodThatThrows() {
        assertThatThrownBy(proxy::explode).isInstanceOf(IllegalStateException.class);

        verify(auditTrail, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("entityId literally null in the annotation is passed through as a null UUID, not evaluated as an error")
    void literalNullEntityIdIsAllowed() {
        when(currentUserProvider.require()).thenReturn(caller());

        proxy.replaceInstitutionWide();

        verify(auditTrail).record(any(), any(), eq("THING_REPLACED"), eq("Thing"), isNull(), isNull());
    }
}
