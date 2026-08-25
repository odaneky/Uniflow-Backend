package com.university.lms.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.service.AuditableAspect;
import com.university.lms.curriculum.api.CourseSubstitutions;
import com.university.lms.curriculum.repository.CourseSubstitutionRepository;
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
 * B3: {@code DefaultCourseSubstitutions.record} is void and its {@code @Auditable} details
 * expression concatenates two SpEL variables ({@code #requiredCourseId} into a string literal) —
 * a shape not exercised by any other {@code @Auditable} method yet, so it gets its own targeted
 * proxy test rather than relying on {@code AuditableAspectTest}'s generic coverage.
 */
@ExtendWith(MockitoExtension.class)
class DefaultCourseSubstitutionsAuditTest {

    @Mock
    private CourseSubstitutionRepository repository;

    @Mock
    private AuditTrail auditTrail;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CourseSubstitutions proxy;

    @BeforeEach
    void setUp() {
        DefaultCourseSubstitutions target = new DefaultCourseSubstitutions(repository);
        AuditableAspect aspect = new AuditableAspect(auditTrail, currentUserProvider);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    @Test
    @DisplayName("recording a substitution is scoped to the student and names the required course in details")
    void recordingASubstitutionWritesAnAuditEventScopedToTheStudent() {
        UUID studentId = UUID.randomUUID();
        UUID requiredCourseId = UUID.randomUUID();
        UUID substituteCourseId = UUID.randomUUID();
        UUID serviceRequestId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(new CurrentUser(
                approvedBy, "idp-subject", "registrar", "registrar@example.edu", "Rita Registrar",
                Optional.empty(), Set.of("REGISTRAR"), Set.of()));
        when(repository.findByStudentIdAndRequiredCourseId(studentId, requiredCourseId)).thenReturn(Optional.empty());

        proxy.record(studentId, requiredCourseId, substituteCourseId, serviceRequestId, approvedBy);

        verify(auditTrail).record(
                any(), eq("Rita Registrar"), eq(AuditTrail.Action.COURSE_SUBSTITUTION_RECORDED),
                eq(AuditTrail.EntityType.STUDENT), eq(studentId),
                eq("Substitutes for required course " + requiredCourseId));
    }
}
