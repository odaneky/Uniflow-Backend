package com.university.lms.support;

/**
 * Resolves which PostgreSQL the integration tests run against.
 *
 * <p>Testcontainers is the default and is what CI should use, because it guarantees a clean,
 * version-pinned database with no shared state. It is not, however, always available: a container
 * runtime may be absent, or — as encountered on Docker Engine 29 with Testcontainers 1.20.x — the
 * bundled client may be unable to negotiate with the daemon at all.
 *
 * <p>Rather than let that silently skip the tests that cover the most consequential behaviour in
 * the system, an already-running database can be supplied instead:
 *
 * <pre>{@code
 * ./mvnw verify -Dlms.test.datasource.url=jdbc:postgresql://localhost:5432/university_lms_test
 * }</pre>
 *
 * <p>Point this at a <em>dedicated</em> database. Flyway migrates whatever it is given, so aiming
 * it at a development database would rewrite that schema.
 */
public final class TestDatabase {

    public static final String URL_PROPERTY = "lms.test.datasource.url";
    public static final String USERNAME_PROPERTY = "lms.test.datasource.username";
    public static final String PASSWORD_PROPERTY = "lms.test.datasource.password";

    private static final String URL_ENV = "LMS_TEST_DATASOURCE_URL";
    private static final String USERNAME_ENV = "LMS_TEST_DATASOURCE_USERNAME";
    private static final String PASSWORD_ENV = "LMS_TEST_DATASOURCE_PASSWORD";

    private TestDatabase() {}

    /** True when an externally managed database has been supplied. */
    public static boolean isExternalConfigured() {
        String url = url();
        return url != null && !url.isBlank();
    }

    public static String url() {
        return resolve(URL_PROPERTY, URL_ENV, null);
    }

    public static String username() {
        return resolve(USERNAME_PROPERTY, USERNAME_ENV, "lms");
    }

    public static String password() {
        return resolve(PASSWORD_PROPERTY, PASSWORD_ENV, "lms");
    }

    private static String resolve(String property, String environmentVariable, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
