package com.university.lms.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal reader for {@code CREATE TABLE} DDL, used to compare two descriptions of the same
 * schema: the one Hibernate derives from the entity mappings, and the one Flyway actually applies.
 *
 * <p>This exists because {@code ddl-auto: validate} only catches a mapping/migration mismatch when
 * a real database is available. Parsing both sides with the same reader makes that check runnable
 * anywhere, including a machine with no Docker.
 */
public final class SqlSchema {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([\\w\".]+)\\s*\\((.*?)\\)\\s*;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * A whole {@code ALTER TABLE ... ;} statement, captured so its clauses can be read together.
     *
     * <p>Matching individual {@code ADD COLUMN} clauses directly does not work: one statement may
     * carry several, separated by commas, and a per-clause pattern either stops at the first comma
     * inside a type such as {@code numeric(5,2)} or runs past the end of the clause and swallows
     * the rest of the statement as the column's type. Both were observed. Reading the statement
     * first and then walking its clauses is the only form that handles real migrations.
     */
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "alter\\s+table\\s+(?:if\\s+exists\\s+)?([\\w\".]+)([^;]*);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * {@code DROP TABLE}. A reader that only understands creation drifts out of date the first time
     * a migration removes something, and then reports the removed object as an orphan forever —
     * training everyone to ignore the check that is meant to catch real drift.
     */
    private static final Pattern DROP_TABLE = Pattern.compile(
            "drop\\s+table\\s+(?:if\\s+exists\\s+)?([\\w\".]+)\\s*(?:cascade|restrict)?\\s*;",
            Pattern.CASE_INSENSITIVE);

    /** Lines that describe a constraint rather than a column. */
    private static final Set<String> CONSTRAINT_KEYWORDS =
            Set.of("constraint", "primary", "foreign", "unique", "check", "exclude");

    private final Map<String, Map<String, String>> tables = new LinkedHashMap<>();

    private SqlSchema() {}

    /** Parses every {@code CREATE TABLE} in the supplied DDL. */
    public static SqlSchema parse(String ddl) {
        SqlSchema schema = new SqlSchema();
        String stripped = stripComments(ddl);
        Matcher matcher = CREATE_TABLE.matcher(stripped);
        while (matcher.find()) {
            String table = normaliseIdentifier(matcher.group(1));
            schema.tables.put(table, parseColumns(matcher.group(2)));
        }

        // Applied after every CREATE TABLE, so a column added by a later migration lands on the
        // table it belongs to regardless of the order the files were concatenated in. Removals are
        // handled in the same pass, in statement order, so a column added and later dropped ends
        // up absent.
        Matcher altered = ALTER_TABLE.matcher(stripped);
        while (altered.find()) {
            Map<String, String> columns = schema.tables.get(normaliseIdentifier(altered.group(1)));
            if (columns == null) {
                continue;
            }
            applyAlterClauses(columns, altered.group(2));
        }

        Matcher droppedTable = DROP_TABLE.matcher(stripped);
        while (droppedTable.find()) {
            schema.tables.remove(normaliseIdentifier(droppedTable.group(1)));
        }

        return schema;
    }

    public Set<String> tableNames() {
        return tables.keySet();
    }

    public Map<String, String> columnsOf(String table) {
        return tables.getOrDefault(normaliseIdentifier(table), Map.of());
    }

    public boolean hasTable(String table) {
        return tables.containsKey(normaliseIdentifier(table));
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /**
     * Walks the clauses of one {@code ALTER TABLE} statement.
     *
     * <p>Clauses are split at top level, so a comma inside {@code numeric(5,2)} does not end one.
     * Anything that is not an add or a drop of a column — a constraint, a default, a rename — is
     * ignored: this reader compares columns and types, and understanding more would be a liability
     * rather than an improvement.
     */
    private static void applyAlterClauses(Map<String, String> columns, String clauses) {
        for (String clause : splitTopLevel(clauses)) {
            String trimmed = clause.trim().replaceFirst("(?i)^alter\\s+table\\s+[\\w\".]+\\s+", "");
            Matcher add = ADD_COLUMN_CLAUSE.matcher(trimmed);
            if (add.find()) {
                columns.put(normaliseIdentifier(add.group(1)), normaliseType(add.group(2).trim()));
                continue;
            }
            Matcher drop = DROP_COLUMN_CLAUSE.matcher(trimmed);
            if (drop.find()) {
                columns.remove(normaliseIdentifier(drop.group(1)));
            }
        }
    }

    private static final Pattern ADD_COLUMN_CLAUSE = Pattern.compile(
            "^add\\s+column\\s+(?:if\\s+not\\s+exists\\s+)?([\\w\"]+)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern DROP_COLUMN_CLAUSE = Pattern.compile(
            "^drop\\s+column\\s+(?:if\\s+exists\\s+)?([\\w\"]+)",
            Pattern.CASE_INSENSITIVE);

    private static Map<String, String> parseColumns(String body) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (String definition : splitTopLevel(body)) {
            String trimmed = definition.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String firstWord = trimmed.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (CONSTRAINT_KEYWORDS.contains(firstWord)) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length < 2) {
                continue;
            }
            columns.put(normaliseIdentifier(parts[0]), normaliseType(parts[1]));
        }
        return columns;
    }

    /** Splits on commas that are not nested inside parentheses, e.g. {@code numeric(7,2)}. */
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    private static String normaliseIdentifier(String raw) {
        return raw.trim().replace("\"", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Reduces a column definition to a comparable base type.
     *
     * <p>The two sides spell the same type differently — Hibernate emits
     * {@code timestamp(6) with time zone} where the migration says {@code timestamptz} — so
     * lengths and precisions are dropped and known aliases are folded together. Length is not
     * compared because Hibernate's own validator does not enforce it either.
     */
    public static String normaliseTypeName(String rawType) {
        return normaliseType(rawType);
    }

    private static String normaliseType(String definition) {
        String type = definition.trim().toLowerCase(Locale.ROOT);

        // Drop trailing column constraints, keeping only the type itself.
        type = type.replaceAll(
                "\\s+(not\\s+null|null|primary\\s+key|unique|default\\s+.*|references\\s+.*|check\\s*\\(.*|generated\\s+.*)$",
                "");
        type = type.replaceAll("\\s+not\\s+null.*$", "");
        type = type.replaceAll("\\s+primary\\s+key.*$", "");
        type = type.replaceAll("\\s+unique.*$", "");
        type = type.replaceAll("\\s+default\\s+.*$", "");
        type = type.replaceAll("\\s+references\\s+.*$", "");

        // Remove length / precision, which neither side is required to agree on.
        type = type.replaceAll("\\(\\s*\\d+\\s*(,\\s*\\d+\\s*)?\\)", "");
        type = type.replaceAll("\\s+", " ").trim();

        return switch (type) {
            case "timestamptz", "timestamp with time zone" -> "timestamptz";
            case "int", "int4", "integer" -> "integer";
            case "int8", "bigint" -> "bigint";
            case "bool", "boolean" -> "boolean";
            case "decimal", "numeric" -> "numeric";
            case "character varying", "varchar" -> "varchar";
            case "text" -> "varchar";
            case "float8", "double precision" -> "double precision";
            default -> type;
        };
    }
}
