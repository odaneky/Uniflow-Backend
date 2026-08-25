package com.university.lms.administration.service;

import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import java.lang.reflect.Method;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Writes an {@link AuditTrail} event automatically for every {@link Auditable}-annotated method
 * that returns without throwing — see that annotation's javadoc for the SpEL contract and the
 * self-invocation caveat inherent to any Spring AOP proxy.
 *
 * <p>Nothing this advice does is allowed to fail the write it is auditing — the same invariant
 * {@code DefaultAuditTrail} already keeps around its own persistence call, kept consistently across
 * the whole annotation-driven path rather than just the last step of it. A bad SpEL expression in
 * the annotation is a programmer error, not a runtime data problem, but it is still only caught
 * here and logged rather than thrown: the alternative is a real, already-succeeded write rolling
 * back because its audit record could not be described, which is a worse failure than a gap in the
 * trail. This is exactly why {@code AuditableAspectTest} and {@code FinanceAuditIntegrationTest}
 * exist — a broken expression must be caught by a test that asserts the resulting event, since
 * nothing at runtime will surface it as an error.
 */
@Aspect
@Component
public class AuditableAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditableAspect.class);

    private final AuditTrail auditTrail;
    private final CurrentUserProvider currentUserProvider;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    public AuditableAspect(AuditTrail auditTrail, CurrentUserProvider currentUserProvider) {
        this.auditTrail = auditTrail;
        this.currentUserProvider = currentUserProvider;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void audit(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            EvaluationContext context = contextFor(joinPoint, result);
            UUID entityId = evaluate(auditable.entityId(), context, UUID.class);
            String details =
                    auditable.details().isBlank() ? null : evaluate(auditable.details(), context, String.class);

            // require(), not find(): an @Auditable method is only ever reached through
            // SecurityConfig's gate, so a real authenticated caller always exists by the time this
            // advice runs — but find() never provisions a local row for a first-time caller, and
            // several @Auditable methods (the finance registry-configuration writes, for instance)
            // have no service-layer guard of their own to have already provisioned one earlier in
            // the same call. Using find() here would silently record a null actor for exactly the
            // callers most worth naming: someone acting in a new role for the first time.
            CurrentUser caller = currentUserProvider.require();

            auditTrail.record(
                    caller.userId(), caller.fullName(), auditable.action(), auditable.entityType(), entityId,
                    details);
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to write @Auditable event {} for {} from {}",
                    auditable.action(),
                    auditable.entityType(),
                    joinPoint.getSignature(),
                    ex);
        }
    }

    private EvaluationContext contextFor(JoinPoint joinPoint, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String[] names = parameterNames.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        context.setVariable("result", result);
        return context;
    }

    private <T> T evaluate(String expression, EvaluationContext context, Class<T> type) {
        Expression parsed = parser.parseExpression(expression);
        return parsed.getValue(context, type);
    }
}
