package com.university.lms.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Enforces the rule stated in {@code docs/architecture.md} and {@code docs/modules.md}: a module
 * may not touch another module's internals, only its published {@code api} package. Until now this
 * was a rule the docs asserted and nothing checked — every fix in this codebase that reaches across
 * a module boundary has been a judgment call verified by reading, not by the build.
 *
 * <p>Deliberately no Spring context and no database: {@link ClassFileImporter} reads compiled
 * bytecode directly, the same way {@code SchemaMigrationConsistencyTest} reads migration SQL
 * directly, so this runs in under a second and needs nothing but {@code target/classes}.
 *
 * <p>Scoped to main sources only ({@link ImportOption.Predefined#DO_NOT_INCLUDE_TESTS}). Test
 * fixtures such as {@code OwnerScopingFixtures} deliberately reach into several modules' repositories
 * for setup convenience — a reasonable trade for readable tests, and a different concern from
 * whether production code respects the boundary.
 *
 * <h2>Why this is frozen, not strict, on most modules</h2>
 *
 * Running this the first time found 11 of 19 modules already reaching past each other's {@code api}
 * package — {@code assessment}, {@code course}, {@code curriculum}, {@code enrollment},
 * {@code finance}, {@code financialaid}, {@code grading}, {@code identity}, {@code notification},
 * {@code request}, {@code student}. One of those — {@code request} → {@code financialaid} in
 * {@code SapAppealFulfillmentApplier} — was cheap and well-understood enough to fix on the spot
 * (see {@code financialaid.api.HoldActions}); the rest is real, pre-existing debt spread across
 * roughly fifty call sites, most of it plain-data reads (a getter, an enum constant, a DTO
 * constructor) rather than genuine behavioural coupling.
 *
 * <p>{@link FreezingArchRule} is ArchUnit's built-in answer to exactly this situation: it persists
 * today's violations to {@code src/test/resources/archunit_store}, lets them keep passing, and fails
 * the build the moment a violation is added that is not already in that store. So this test is live
 * and enforcing from today — it just enforces "no new boundary violations" on the 11 already-crossed
 * modules, and "no violations at all" on the other 8, rather than requiring the debt to be paid down
 * in the same change that adds the check. Fix one of the frozen call sites and the stored baseline
 * shrinks on your next local run — nobody has to hand-edit the store to get credit for it.
 */
class ModuleBoundaryTest {

    private static final String BASE_PACKAGE = "com.university.lms";

    /** Infrastructure with no bounded contract of its own — depended on by every module, and the
     *  one package every module may reach into freely without going through an {@code api}. */
    private static final String COMMON_MODULE = "common";

    private static JavaClasses mainClasses;
    private static List<String> modules;

    @BeforeAll
    static void importMainClasses() throws IOException {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath("target/classes");
        modules = discoverModules();
    }

    /**
     * The module list is read from the source tree, not hard-coded, so a new module added later is
     * covered automatically rather than silently exempt until someone remembers to update this test.
     */
    private static List<String> discoverModules() throws IOException {
        Path root = Path.of("src/main/java", BASE_PACKAGE.replace('.', '/'));
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> !name.equals(COMMON_MODULE))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void discoveredAtLeastTheModulesThisTestKnowsAbout() {
        // A sanity check on the discovery mechanism itself, independent of the rule below: if this
        // finds an implausibly small module count, the boundary rule beneath it is checking nothing.
        assertThat(modules).contains("academic", "course", "enrollment", "grading", "student", "curriculum");
    }

    @TestFactory
    Stream<DynamicTest> aModulesInternalsAreReachedOnlyThroughItsApiPackage() {
        return modules.stream().map(module -> DynamicTest.dynamicTest(module, () -> {
            String modulePackage = BASE_PACKAGE + "." + module;
            ArchRule rule = classes()
                    .that()
                    .resideInAPackage(modulePackage + "..")
                    .and()
                    .resideOutsideOfPackage(modulePackage + ".api..")
                    .should()
                    .onlyBeAccessed()
                    .byAnyPackage(modulePackage + "..", BASE_PACKAGE + "." + COMMON_MODULE + "..")
                    .as("classes in " + modulePackage + " outside its api package are reached only from "
                            + "within " + module + " (or from " + COMMON_MODULE + ", which nothing may "
                            + "depend on, so it cannot be the wrong side of this)");
            FreezingArchRule.freeze(rule).check(mainClasses);
        }));
    }

    @Test
    void commonDependsOnNoOtherModule() {
        // Not frozen: this one was clean when the test was introduced, and should stay strict —
        // freezing a rule with nothing to freeze is a no-op today, but a plain rule fails louder
        // and sooner if that ever stops being true.
        String[] otherModules = modules.stream()
                .map(module -> BASE_PACKAGE + "." + module + "..")
                .toArray(String[]::new);
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(BASE_PACKAGE + "." + COMMON_MODULE + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(otherModules)
                .as(COMMON_MODULE + " is depended on by every module and must depend on none of them");
        rule.check(mainClasses);
    }
}
