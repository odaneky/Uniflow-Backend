package com.university.lms.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that exercise the full stack against real PostgreSQL.
 *
 * <p>By default a Testcontainers instance is started once per JVM and shared by every subclass,
 * rather than being torn down and rebuilt per test class. Schema creation goes through Flyway
 * exactly as it does in production, so these tests also verify the migrations themselves.
 *
 * <p>When {@link TestDatabase#isExternalConfigured()} reports a supplied database, no container is
 * started and that database is used instead — see {@link TestDatabase} for why that escape hatch
 * exists and how to point it somewhere safe.
 */
@SpringBootTest
@ActiveProfiles("test")
@RequiresDocker
public abstract class AbstractPostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (TestDatabase.isExternalConfigured()) {
            POSTGRES = null;
        } else {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("university_lms_test")
                    .withUsername("lms")
                    .withPassword("lms")
                    .withReuse(true);
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) {
            registry.add("spring.datasource.url", TestDatabase::url);
            registry.add("spring.datasource.username", TestDatabase::username);
            registry.add("spring.datasource.password", TestDatabase::password);
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }
}
