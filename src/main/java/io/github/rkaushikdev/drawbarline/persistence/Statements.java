package io.github.rkaushikdev.drawbarline.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every SQL statement a database is sent, as it is sent.
 *
 * <p>One of these sits on each {@link Database}. It echoes the statements to the log when the
 * settings ask for it, and it hands them to whoever is {@link #watch() watching}, which is how a
 * test pins the shape of a read: one aggregate for a count, not a query a row.
 */
public final class Statements implements StatementInspector {

    /** Where echoed statements go, under the application's logger root. */
    public static final String LOGGER = "drawbarline.persistence.sql";

    private static final Logger ECHO = LoggerFactory.getLogger(LOGGER);

    private final boolean echo;
    private final List<Watch> watching = new CopyOnWriteArrayList<>();

    Statements(boolean echo) {
        this.echo = echo;
    }

    @Override
    public String inspect(String sql) {
        if (echo) {
            ECHO.info(sql);
        }
        for (Watch one : watching) {
            one.seen(sql);
        }
        return sql;
    }

    /** Start collecting the statements sent from now until the watch is closed. */
    public Watch watch() {
        Watch found = new Watch(this);
        watching.add(found);
        return found;
    }

    /** The statements seen while a watch was open. Closing it stops the collecting. */
    public static final class Watch implements AutoCloseable {

        private final Statements source;
        private final List<String> seen = new CopyOnWriteArrayList<>();

        private Watch(Statements source) {
            this.source = source;
        }

        private void seen(String sql) {
            seen.add(sql);
        }

        /** Every statement seen so far, in the order it was sent. */
        public List<String> statements() {
            return List.copyOf(new ArrayList<>(seen));
        }

        /** How many statements were sent. */
        public int count() {
            return seen.size();
        }

        @Override
        public void close() {
            source.watching.remove(this);
        }
    }
}
