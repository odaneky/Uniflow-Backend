package com.university.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.identity.repository.RoleRepository;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The single most valuable integration test in the suite: it starts the whole application against
 * real PostgreSQL.
 *
 * <p>Because the application runs with {@code ddl-auto: validate}, merely reaching a started
 * context proves three things at once — every migration applied cleanly, the resulting schema
 * satisfies every JPA mapping, and the seeded reference data is present. A drift between an entity
 * and a migration cannot survive this test.
 */
class ApplicationBootstrapIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("the context starts, which means Flyway migrated and Hibernate validated the schema")
    void contextLoadsAgainstMigratedSchema() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    @DisplayName("every migration is recorded as successful")
    void allMigrationsApplied() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer failed = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        assertThat(failed).isZero();

        Integer applied =
                jdbc.queryForObject("select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(14);
    }

    @Test
    @DisplayName("reference data seeded by migration is available to the application")
    void referenceDataIsSeeded() {
        assertThat(roleRepository.findByName("REGISTRAR")).isPresent();
        assertThat(roleRepository.findByName("STUDENT")).isPresent();
        assertThat(roleRepository.count()).isGreaterThanOrEqualTo(6);
    }
}
