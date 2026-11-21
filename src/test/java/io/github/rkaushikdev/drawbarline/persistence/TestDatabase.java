package io.github.rkaushikdev.drawbarline.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The suite's books: one H2 database in memory, migrated once for the whole run and emptied before
 * each test that asks for it.
 *
 * <p>Building an entity manager factory costs more than the tests do, so this keeps one database
 * and one factory and gives every test a database of its own in effect: the schema, nothing in it,
 * and generated keys counting from one again.
 */
public final class TestDatabase {

    public static final String URL = "jdbc:h2:mem:drawbarline-tests";

    private static Database shared;

    private TestDatabase() {}

    /** The shared books, emptied. */
    public static synchronized Database fresh() {
        if (shared == null) {
            shared = Database.open(URL);
            shared.migrate();
        }
        empty(shared);
        return shared;
    }

    /** Fresh books, filled by {@code work} in one unit of work that commits. */
    public static Database seeded(Consumer<UnitOfWork> work) {
        Database database = fresh();
        try (UnitOfWork unit = database.unitOfWork()) {
            work.accept(unit);
            unit.commit();
        }
        return database;
    }

    private static void empty(Database database) {
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String table : database.tableNames()) {
                statement.execute("TRUNCATE TABLE \"" + table.toUpperCase(Locale.ROOT) + "\" RESTART IDENTITY");
            }
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (SQLException trouble) {
            throw new AssertionError("the test database could not be emptied", trouble);
        }
    }
}
