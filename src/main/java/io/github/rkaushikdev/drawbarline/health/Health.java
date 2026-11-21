package io.github.rkaushikdev.drawbarline.health;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.Numbering;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.util.Literal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Whether a set of books is ready to be worked.
 *
 * <p>Half the support calls this office gets are a database that was built but never had its
 * filings loaded, so a close runs, produces nothing, and shuts a month that had traffic in it.
 * Nothing about that is an error anywhere: every step did what it was told.
 *
 * <p>So this asks the questions somebody would ask before running a close, and answers them one
 * at a time. Each check knows what it is for and says what to do about it, because a check that
 * reports a fault and not a remedy is a check somebody has to interpret.
 */
public final class Health {

    private Health() {}

    /** Whether every table the books are mapped onto is in the database. */
    public static Finding schema(Database database) {
        List<String> found = database.tableNames();
        Set<String> missing = new TreeSet<>(Database.modelTables());
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            return new Finding(
                    "schema",
                    Level.BAD,
                    missing.size() + " tables missing",
                    "migrate the schema to the latest revision");
        }
        return new Finding("schema", Level.GOOD, found.size() + " tables");
    }

    /** Whether every document number series has been opened. */
    public static Finding counters(UnitOfWork work) {
        List<String> open = work.numbering().openSeries();
        Set<String> missing = new TreeSet<>(Numbering.SERIES);
        missing.removeAll(open);
        if (!missing.isEmpty()) {
            return new Finding(
                    "numbering",
                    Level.BAD,
                    Literal.of(List.copyOf(missing)) + " not opened",
                    "run init on a fresh database");
        }
        return new Finding("numbering", Level.GOOD, open.size() + " series open");
    }

    /** Whether there is a car hire schedule to price the month against. */
    public static Finding rates(UnitOfWork work) {
        int filed = work.rates().size();
        if (filed == 0) {
            return new Finding("car hire rates", Level.BAD, "none filed", "reference rates <sheet>");
        }
        return new Finding("car hire rates", Level.GOOD, filed + " bands");
    }

    /** Whether a demurrage tariff is in force on a day. */
    public static Finding tariffs(UnitOfWork work, LocalDate on) {
        Tariff found;
        try {
            found = work.tariffs().inEffectOn(on);
        } catch (DrawbarlineError none) {
            return new Finding("demurrage tariff", Level.BAD, "none in force", "reference tariff ...");
        }
        return new Finding("demurrage tariff", Level.GOOD, "tariff " + found.code());
    }

    /** Whether any industry track is filed, which demurrage is counted on. */
    public static Finding customers(UnitOfWork work) {
        int tracks = work.customers().size();
        if (tracks == 0) {
            return new Finding("industry track", Level.WARN, "none filed", "reference industries <sheet>");
        }
        return new Finding("industry track", Level.GOOD, tracks + " tracks");
    }

    /** Whether there are cars on the register. */
    public static Finding fleet(UnitOfWork work) {
        int cars = work.fleet().size();
        if (cars == 0) {
            return new Finding("fleet", Level.WARN, "no cars on the register", "fleet add ...");
        }
        return new Finding("fleet", Level.GOOD, cars + " cars");
    }

    /** Whether any interchange agreement is in force on a day. */
    public static Finding agreements(UnitOfWork work, LocalDate on) {
        int live = work.agreements().inForceOn(on).size();
        if (live == 0) {
            return new Finding("agreements", Level.WARN, "none in force", "agreement sign ...");
        }
        return new Finding("agreements", Level.GOOD, live + " in force");
    }

    /**
     * Every check, in the order somebody would ask them.
     *
     * <p>The schema check comes first and stops the rest, because every check after it reads a
     * table. Running them anyway turns one clear finding into six operational errors that all say
     * the same thing. The rest are asked in one unit of work that writes nothing.
     */
    public static List<Finding> examine(Database database, LocalDate on) {
        Finding built = schema(database);
        if (built.level().stopsAClose()) {
            return List.of(built);
        }
        try (UnitOfWork work = database.unitOfWork()) {
            return List.of(
                    built,
                    counters(work),
                    rates(work),
                    tariffs(work, on),
                    customers(work),
                    fleet(work),
                    agreements(work, on));
        }
    }

    /** Whether a close would produce anything worth sending out. */
    public static boolean ready(List<Finding> findings) {
        return findings.stream().noneMatch(one -> one.level().stopsAClose());
    }
}
