package com.university.lms.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Skips {@link RequiresDocker} tests when there is no database to run them against.
 *
 * <p>An externally supplied database satisfies the requirement without a container runtime, so it
 * is checked first — and short-circuits the Testcontainers probe, which is slow and, against some
 * daemon versions, fails in ways that take seconds to time out.
 */
public class DockerAvailableCondition implements ExecutionCondition {

    /** Probed once per JVM: the check performs I/O and would otherwise run for every test. */
    private static final boolean DATABASE_AVAILABLE = probe();

    private static boolean probe() {
        if (TestDatabase.isExternalConfigured()) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return DATABASE_AVAILABLE
                ? ConditionEvaluationResult.enabled("A PostgreSQL instance is available")
                : ConditionEvaluationResult.disabled("No PostgreSQL available — set -D" + TestDatabase.URL_PROPERTY
                        + " or start a container runtime; integration tests skipped");
    }
}
