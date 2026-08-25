package com.university.lms.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.api.Auditable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B4: every write in a {@code @Service} class that follows this codebase's read/write-split
 * convention — {@code @Transactional(readOnly = true)} at class level, a bare {@code
 * @Transactional} on the methods that actually write — must be either {@link Auditable} or emit an
 * event by calling {@link AuditTrail#record} directly. The two are equally acceptable: B3 built
 * {@code @Auditable} for the common, structurally-uniform case, but roughly thirty call sites
 * across the identity, grading, enrolment, admissions, request and finance modules already called
 * {@code AuditTrail.record} by hand before {@code @Auditable} existed, correctly, and this test
 * must not ask them to switch mechanisms just to satisfy it.
 *
 * <p>Scope, stated plainly rather than silently: this only sees classes that actually follow the
 * class-level-{@code readOnly}-plus-method-level-{@code @Transactional} convention B3 was built
 * against. A handful of service classes use a different shape entirely — no class-level
 * {@code @Transactional} at all, or a class-level one that is itself writable — and this rule does
 * not reach them. Widening it to a fully general "detect every write method regardless of
 * annotation style" predicate is real, separate work, not a one-line addition; noted here rather
 * than quietly left for someone else to discover.
 *
 * <p>{@link FreezingArchRule}, same mechanism as {@code ModuleBoundaryTest} and (until it was
 * completed) {@code AccessClassCoverageTest}: the write methods B3 did not reach this pass —
 * everywhere outside finance, financialaid, curriculum and academic — keep passing as a frozen,
 * shrinking backlog, while a newly added write method with no audit trail of any kind fails the
 * build immediately rather than joining that backlog unnoticed.
 */
class AuditCoverageTest {

    private static final DescribedPredicate<JavaMethod> IS_A_WRITE_METHOD_IN_A_READ_WRITE_SPLIT_SERVICE =
            new DescribedPredicate<>(
                    "is a @Transactional write method in a @Service class using the class-level "
                            + "readOnly=true / method-level @Transactional convention") {
                @Override
                public boolean test(JavaMethod method) {
                    JavaClass owner = method.getOwner();
                    if (!owner.isAnnotatedWith(Service.class)) {
                        return false;
                    }
                    if (!owner.isAnnotatedWith(Transactional.class)) {
                        return false;
                    }
                    boolean classIsReadOnlyByDefault = owner.getAnnotationOfType(Transactional.class).readOnly();
                    if (!classIsReadOnlyByDefault) {
                        return false;
                    }
                    return method.isAnnotatedWith(Transactional.class)
                            && method.getModifiers().contains(JavaModifier.PUBLIC);
                }
            };

    private static final ArchCondition<JavaMethod> BE_AUDITABLE_OR_CALL_AUDIT_TRAIL_DIRECTLY =
            new ArchCondition<>("be annotated with @Auditable or call AuditTrail.record directly") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean satisfied = method.isAnnotatedWith(Auditable.class) || callsAuditTrailRecord(method);
                    String message = method.getFullName()
                            + (satisfied
                                    ? " is audited"
                                    : " writes state but is neither @Auditable nor calls AuditTrail.record");
                    events.add(new SimpleConditionEvent(method, satisfied, message));
                }

                /**
                 * Not just direct calls: {@code FinanceService.addEntry}, for one real example,
                 * calls a private {@code recordLedgerEntryAudit} helper that calls {@code
                 * AuditTrail.record} — the same "generic X-audit helper" shape noted at several of
                 * the ~30 pre-existing manual call sites B3's own investigation found in
                 * EnrollmentService, CourseService and GradeService. Walks calls to methods
                 * declared in the same class (private helpers do not cross a proxy boundary, so
                 * this is safe to follow, unlike the cross-bean case {@code
                 * AcademicStructureService.replaceCreditLoad} is deliberately NOT credited for),
                 * bounded by a visited-set against runaway recursion on a cycle.
                 */
                private boolean callsAuditTrailRecord(JavaMethod method) {
                    return callsAuditTrailRecord(method, new HashSet<>());
                }

                private boolean callsAuditTrailRecord(JavaMethod method, Set<JavaMethod> visited) {
                    if (!visited.add(method)) {
                        return false;
                    }
                    for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                        JavaClass targetOwner = call.getTarget().getOwner();
                        if (targetOwner.isAssignableTo(AuditTrail.class) && call.getTarget().getName().equals("record")) {
                            return true;
                        }
                        if (targetOwner.equals(method.getOwner())) {
                            Optional<JavaMethod> candidate = call.getTarget().resolveMember();
                            if (candidate.isPresent() && callsAuditTrailRecord(candidate.get(), visited)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            };

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importMainClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath("target/classes");
    }

    @Test
    void everyWriteInAReadWriteSplitServiceEmitsAnAuditEvent() {
        ArchRule rule = methods()
                .that(IS_A_WRITE_METHOD_IN_A_READ_WRITE_SPLIT_SERVICE)
                .should(BE_AUDITABLE_OR_CALL_AUDIT_TRAIL_DIRECTLY)
                .as("every @Transactional write method in a read/write-split @Service class is either "
                        + "@Auditable or calls AuditTrail.record directly — see this test's own javadoc "
                        + "for what it does and does not see");
        FreezingArchRule.freeze(rule).check(mainClasses);
    }
}
