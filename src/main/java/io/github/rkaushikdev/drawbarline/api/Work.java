package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import java.util.Optional;
import java.util.function.Function;

/**
 * What a route needs to reach the books: one database for the process and one unit of work a
 * request.
 *
 * <p>A route that writes runs through {@link #apply}, which commits only if the route returns
 * without raising. That is the same rule the command line follows and for the same reason: a route
 * that fails half way should leave the month exactly as it was. A route that only reads runs
 * through {@link #read}, which never commits, so a read can never leave anything behind however
 * badly somebody writes a handler.
 *
 * <p>The commit happens before the route's answer is written, which is why this is a call around
 * the route's body and not an interceptor: the body of a response is written before an
 * interceptor's after-handler runs, so a commit there that failed would follow a success.
 */
final class Work {

    private final Optional<Database> database;

    Work(Optional<Database> database) {
        this.database = database;
    }

    /** The books, or a refusal when the application was started without any. */
    Database database() {
        return database.orElseThrow(() -> new ConfigurationError("the application has no database"));
    }

    /** Run a route that writes in a fresh unit of work, committed when it returns. */
    <T> T apply(Function<UnitOfWork, T> route) {
        try (UnitOfWork work = database().unitOfWork()) {
            T answer = route.apply(work);
            work.commit();
            return answer;
        }
    }

    /** Run a route that only reads in a fresh unit of work, which is never committed. */
    <T> T read(Function<UnitOfWork, T> route) {
        try (UnitOfWork work = database().unitOfWork()) {
            return route.apply(work);
        }
    }
}
