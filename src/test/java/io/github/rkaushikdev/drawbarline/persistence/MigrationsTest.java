package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * The migrations and the entities have to describe the same schema.
 *
 * <p>An entity changed without a migration is the most common way this kind of application breaks,
 * and it breaks on somebody else's machine, because the developer's schema came from the mapping
 * and theirs from the migrations. So the test builds one of each and compares them table by table:
 * the columns with their types and nullability, the indexes, and the named constraints.
 */
class MigrationsTest {

    private static final Path VERSIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern VERSION = Pattern.compile("V(\\d+)__[a-z0-9_]+\\.sql");
    /** The names H2 gives an index it makes for a constraint nobody indexed. */
    private static final Pattern GENERATED = Pattern.compile(".*_INDEX_[0-9A-F]+");

    private static Database migrated;
    private static HikariDataSource builtPool;
    private static LocalContainerEntityManagerFactoryBean built;

    @BeforeAll
    static void buildBothWays() {
        migrated = Database.inMemory();
        migrated.migrate();
        builtPool = Database.pool("jdbc:h2:mem:built-from-the-mapping");
        built = Database.factoryBean(builtPool, "create", new Statements(false), Database.MAPPED_PACKAGES);
        built.afterPropertiesSet();
    }

    @AfterAll
    static void closeBoth() {
        built.destroy();
        builtPool.close();
        migrated.close();
    }

    static List<String> tables() {
        return Database.modelTables();
    }

    private static Set<String> tablesOf(DataSource source) {
        return rows(
                source,
                "select lower(table_name) from information_schema.tables where table_schema = 'PUBLIC' "
                        + "and lower(table_name) <> ?",
                Database.HISTORY_TABLE);
    }

    private static Set<String> columnsOf(DataSource source, String table) {
        return rows(
                source,
                "select lower(column_name) || ' ' || data_type || '(' || coalesce(character_maximum_length, "
                        + "numeric_precision, datetime_precision, 0) || ') ' || is_nullable "
                        + "from information_schema.columns where table_schema = 'PUBLIC' and table_name = ?",
                table.toUpperCase(Locale.ROOT));
    }

    private static Set<String> indexesOf(DataSource source, String table) {
        Set<String> found = rows(
                source,
                "select lower(index_name) from information_schema.indexes where table_schema = 'PUBLIC' "
                        + "and table_name = ? and index_type_name = 'INDEX'",
                table.toUpperCase(Locale.ROOT));
        found.removeIf(name -> GENERATED.matcher(name.toUpperCase(Locale.ROOT)).matches());
        return found;
    }

    private static Set<String> constraintsOf(DataSource source, String table) {
        return rows(
                source,
                "select constraint_type || ' ' || lower(constraint_name) from information_schema.table_constraints "
                        + "where table_schema = 'PUBLIC' and table_name = ? and constraint_type <> 'PRIMARY KEY'",
                table.toUpperCase(Locale.ROOT));
    }

    private static Set<String> primaryKeyOf(DataSource source, String table) {
        return rows(
                source,
                "select lower(k.column_name) || ' ' || k.ordinal_position "
                        + "from information_schema.table_constraints c join information_schema.key_column_usage k "
                        + "on k.constraint_name = c.constraint_name and k.table_name = c.table_name "
                        + "where c.table_schema = 'PUBLIC' and c.table_name = ? and c.constraint_type = 'PRIMARY KEY'",
                table.toUpperCase(Locale.ROOT));
    }

    private static Set<String> rows(DataSource source, String sql, String parameter) {
        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            TreeSet<String> found = new TreeSet<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    found.add(result.getString(1));
                }
            }
            return found;
        } catch (SQLException trouble) {
            throw new AssertionError(sql, trouble);
        }
    }

    @Test
    void theRevisionsAreNumberedInOneLine() throws IOException {
        List<Integer> versions;
        try (Stream<Path> files = Files.list(VERSIONS)) {
            versions = files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(name -> {
                        Matcher matched = VERSION.matcher(name);
                        assertThat(matched.matches()).as(name).isTrue();
                        return Integer.valueOf(matched.group(1));
                    })
                    .sorted()
                    .toList();
        }
        assertThat(Files.exists(VERSIONS.resolve("V1__baseline.sql"))).isTrue();
        assertThat(versions)
                .isEqualTo(IntStream.rangeClosed(1, versions.size()).boxed().toList());
    }

    @Test
    void theMigrationBuildsEveryTableTheModelsDeclare() {
        assertThat(tablesOf(migrated.dataSource())).isEqualTo(new TreeSet<>(tables()));
    }

    @Test
    void theTwoWaysOfBuildingAgreeOnTheTables() {
        assertThat(tablesOf(migrated.dataSource())).isEqualTo(tablesOf(builtPool));
    }

    @ParameterizedTest
    @MethodSource("tables")
    void theTwoWaysAgreeOnTheColumns(String table) {
        assertThat(columnsOf(migrated.dataSource(), table)).isEqualTo(columnsOf(builtPool, table));
        assertThat(primaryKeyOf(migrated.dataSource(), table)).isEqualTo(primaryKeyOf(builtPool, table));
    }

    @ParameterizedTest
    @MethodSource("tables")
    void theTwoWaysAgreeOnTheIndexes(String table) {
        assertThat(indexesOf(migrated.dataSource(), table)).isEqualTo(indexesOf(builtPool, table));
    }

    @ParameterizedTest
    @MethodSource("tables")
    void theTwoWaysAgreeOnTheNamedConstraints(String table) {
        assertThat(constraintsOf(migrated.dataSource(), table)).isEqualTo(constraintsOf(builtPool, table));
    }

    @Test
    void everyComparisonReadsSomethingAndNoticesADifference() throws SQLException {
        // A comparison that reads nothing on both sides agrees with itself, so each one is shown to
        // read a non-empty set and then to notice a planted difference.
        assertThat(columnsOf(builtPool, "lease")).isNotEmpty();
        assertThat(indexesOf(builtPool, "lease")).contains("ix_lease_car_mark");
        assertThat(constraintsOf(builtPool, "lease")).contains("UNIQUE uq_lease_one_start_a_day");
        assertThat(primaryKeyOf(builtPool, "lease")).containsExactly("id 1");
        try (Database planted = Database.inMemory()) {
            planted.migrate();
            try (Connection connection = planted.dataSource().getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("alter table lease add column planted integer");
                statement.execute("create index ix_planted on lease (lessee, began)");
                statement.execute("alter table lease add constraint uq_planted unique (lessee, began)");
                statement.execute("alter table lease drop constraint ck_lease_ends_after");
            }
            assertThat(columnsOf(planted.dataSource(), "lease")).isNotEqualTo(columnsOf(builtPool, "lease"));
            assertThat(indexesOf(planted.dataSource(), "lease")).isNotEqualTo(indexesOf(builtPool, "lease"));
            assertThat(constraintsOf(planted.dataSource(), "lease")).isNotEqualTo(constraintsOf(builtPool, "lease"));
            assertThat(columnsOf(planted.dataSource(), "car")).isEqualTo(columnsOf(builtPool, "car"));
        }
    }

    @Test
    void theMigrationCanBeTakenBackOff() {
        try (Database down = Database.inMemory()) {
            down.migrate();
            down.drop();
            assertThat(down.tableNames()).isEmpty();
        }
    }

    @Test
    void theMigrationCanBeRunTwiceOver() {
        try (Database twice = Database.inMemory()) {
            twice.migrate();
            twice.drop();
            twice.migrate();
            assertThat(twice.tableNames()).isEqualTo(tables());
        }
    }
}
