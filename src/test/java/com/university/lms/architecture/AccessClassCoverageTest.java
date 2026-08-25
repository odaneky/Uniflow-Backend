package com.university.lms.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.university.lms.common.security.AccessClass;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Every controller endpoint must declare an {@link AccessClass}. The catch-all
 * {@code GET /api/v1/** -> authenticated()} in {@code SecurityConfig} is fail-<em>open</em>: a new
 * endpoint with no narrower rule is reachable by any signed-in caller, silently, with nothing to
 * catch the omission before it ships. This test is what catches it — a controller method with no
 * {@code @AccessClass} fails the build, the same way a module-boundary violation does in
 * {@code ModuleBoundaryTest}.
 *
 * <p>Strict, not frozen: at introduction all 220 endpoints across 57 controllers were unannotated,
 * and this ran as a {@code FreezingArchRule} so the rule could be enforced from day one without
 * blocking on the whole backlog landing in one change (see {@code ModuleBoundaryTest}'s javadoc for
 * that mechanism). The annotation sweep is now complete — every controller endpoint (104 GET, 138
 * across the other four methods) carries an {@code @AccessClass}, confirmed by an empty frozen-
 * violation store — so this reverts to a plain rule. A plain rule fails louder and sooner than a
 * frozen one that happens to have nothing left frozen: it does not depend on a store file staying
 * intact, and does not admit a violation that a corrupted or reset store would silently re-allow.
 */
class AccessClassCoverageTest {

    /**
     * "Any of the five Spring MVC mapping annotations, on a class named *Controller" — deliberately
     * not ArchUnit's fluent {@code .or()} chaining, which does not compose the way its syntax
     * suggests across mixed {@code that()}/{@code areDeclaredInClassesThat()} predicates; a direct
     * {@link DescribedPredicate} says exactly what it means.
     */
    private static final List<Class<? extends java.lang.annotation.Annotation>> MAPPING_ANNOTATIONS =
            List.of(GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private static final DescribedPredicate<JavaMethod> IS_CONTROLLER_ENDPOINT =
            new DescribedPredicate<>("is a *Controller endpoint method") {
                @Override
                public boolean test(JavaMethod method) {
                    if (!method.getOwner().getSimpleName().endsWith("Controller")) {
                        return false;
                    }
                    return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith);
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
    void everyControllerEndpointDeclaresAnAccessClass() {
        ArchRule rule = methods()
                .that(IS_CONTROLLER_ENDPOINT)
                .should()
                .beAnnotatedWith(AccessClass.class)
                .as("every controller endpoint method (@GetMapping/@PostMapping/@PutMapping/"
                        + "@PatchMapping/@DeleteMapping) declares an @AccessClass — see that "
                        + "annotation's javadoc for why");
        rule.check(mainClasses);
    }
}
