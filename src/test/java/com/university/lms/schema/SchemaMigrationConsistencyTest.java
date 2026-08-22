package com.university.lms.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.university.lms.support.SqlSchema;
import jakarta.persistence.Entity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Proves that the JPA mappings and the Flyway migrations describe the same schema.
 *
 * <p>The application runs with {@code ddl-auto: validate}, so a mapping that has drifted from the
 * migrations is a startup failure in every environment. That check normally needs a live database;
 * this test performs the equivalent comparison offline, by building Hibernate's metadata model
 * against the PostgreSQL dialect (no connection required) and diffing the tables and columns it
 * expects against the ones the migrations actually create.
 *
 * <p>It therefore runs everywhere — including agents and developer machines without Docker — and
 * fails the moment an entity gains a field that no migration adds, which is when the mistake is
 * cheapest to fix.
 */
class SchemaMigrationConsistencyTest {

    private static final String ENTITY_PACKAGE = "com.university.lms";
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    /** Created and owned by Flyway itself, so it is absent from both the mappings and our DDL. */
    private static final Set<String> NOT_MAPPED_BY_DESIGN = Set.of("flyway_schema_history");

    @Test
    @DisplayName("every mapped table and column exists in the migrations with a matching type")
    void mappingsAgreeWithMigrations() {
        Map<String, Map<String, String>> expected = expectedSchemaFromMappings();
        SqlSchema actual = SqlSchema.parse(readMigrations());

        assertThat(expected).as("sanity check: entity scanning found mapped tables").isNotEmpty();

        List<String> problems = new ArrayList<>();

        expected.forEach((table, expectedColumns) -> {
            if (!actual.hasTable(table)) {
                problems.add("missing table: " + table);
                return;
            }
            Map<String, String> actualColumns = actual.columnsOf(table);
            expectedColumns.forEach((column, expectedType) -> {
                String actualType = actualColumns.get(column);
                if (actualType == null) {
                    problems.add("missing column: " + table + "." + column + " (expected " + expectedType + ")");
                } else if (!actualType.equals(expectedType)) {
                    problems.add("type mismatch: " + table + "." + column + " — mapping expects " + expectedType
                            + ", migration declares " + actualType);
                }
            });
        });

        if (!problems.isEmpty()) {
            problems.sort(Comparator.naturalOrder());
            fail("JPA mappings and Flyway migrations disagree:\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    @DisplayName("migrations do not create tables that no entity maps")
    void migrationsDoNotContainOrphanTables() {
        Set<String> mapped = expectedSchemaFromMappings().keySet();
        SqlSchema actual = SqlSchema.parse(readMigrations());

        Set<String> orphans = new TreeSet<>(actual.tableNames());
        orphans.removeAll(mapped);
        orphans.removeAll(NOT_MAPPED_BY_DESIGN);

        assertThat(orphans)
                .as("tables created by a migration but mapped by no entity — dead schema, or a missing @Entity")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The tables and column types Hibernate derives from the annotated classes. */
    private Map<String, Map<String, String>> expectedSchemaFromMappings() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                // No datasource is available, so Hibernate must not try to read JDBC metadata.
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            scanEntityClasses().forEach(sources::addAnnotatedClass);
            Metadata metadata = sources.buildMetadata();

            Map<String, Map<String, String>> schema = new LinkedHashMap<>();
            for (Namespace namespace : metadata.getDatabase().getNamespaces()) {
                for (Table table : namespace.getTables()) {
                    if (!table.isPhysicalTable()) {
                        continue;
                    }
                    Map<String, String> columns = new LinkedHashMap<>();
                    for (Column column : table.getColumns()) {
                        columns.put(
                                column.getName().toLowerCase(Locale.ROOT),
                                SqlSchema.normaliseTypeName(column.getSqlType(metadata)));
                    }
                    schema.put(table.getName().toLowerCase(Locale.ROOT), columns);
                }
            }
            return schema;
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private List<Class<?>> scanEntityClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
            try {
                classes.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Scanned entity could not be loaded", e);
            }
        }
        return classes;
    }

    private String readMigrations() {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            List<Path> ordered =
                    files.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();

            StringBuilder combined = new StringBuilder();
            for (Path file : ordered) {
                combined.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            }
            return combined.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read migrations from " + MIGRATION_DIR.toAbsolutePath(), e);
        }
    }
}
