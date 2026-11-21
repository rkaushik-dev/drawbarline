package io.github.rkaushikdev.drawbarline.persistence;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.support.Cars;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How many round trips a read costs.
 *
 * <p>Nothing here is slow enough for anybody to notice, which is exactly why a query a car creeps
 * in: it is written once, it works, and it only shows up when the fleet is four times the size.
 * Counting the statements a call sends pins the shape of the read rather than its speed. The
 * numbers are deliberately loose: they catch a read that went from one query to a query a row, not
 * one that gained a column.
 */
class QueryCountsTest {

    private static final ReportingMark CN = new ReportingMark("CN");
    private static final int CARS = 12;

    private Database books;

    @BeforeEach
    void stocked() {
        books = TestDatabase.seeded(work -> {
            for (int at = 0; at < CARS; at++) {
                String mark = "CN " + (400100 + at);
                work.fleet().add(Cars.aCar(mark));
                CarMark car = CarMark.parse(mark);
                work.events()
                        .record(Event.builder(EventKind.RECEIVED, car, utc(2025, 1, 6, 12), "ESC")
                                .road(CN)
                                .build());
                work.events()
                        .record(Event.builder(EventKind.MOVED, car, utc(2025, 1, 6, 18), "PWR")
                                .tenths(336)
                                .build());
            }
        });
    }

    /** The selects sent while {@code reading} runs in a unit of work of its own. */
    private List<String> selects(Consumer<UnitOfWork> reading) {
        try (UnitOfWork work = books.unitOfWork();
                Statements.Watch seen = books.statements().watch()) {
            reading.accept(work);
            return seen.statements().stream()
                    .filter(one -> one.strip().toUpperCase(Locale.ROOT).startsWith("SELECT"))
                    .toList();
        }
    }

    @Test
    void theFixtureHasCarsAndEvents() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.fleet().size()).isEqualTo(CARS);
            assertThat(work.events().size()).isEqualTo(CARS * 2);
        }
    }

    @Test
    void theWholeLogLoadsInOneQuery() {
        List<EventLog> log = new ArrayList<>();
        List<String> seen = selects(work -> log.add(work.events().log()));
        assertThat(log.get(0).size()).isEqualTo(CARS * 2);
        assertThat(seen).hasSize(1);
    }

    @Test
    void loadingTheLogDoesNotGrowWithTheFleet() {
        assertThat(selects(work -> work.events().log())).hasSizeLessThan(CARS);
    }

    @Test
    void oneCarStreamIsOneQuery() {
        assertThat(selects(work -> work.events().stream(CarMark.parse("CN 400100"))))
                .hasSize(1);
    }

    @Test
    void theRateTableLoadsInOneQuery() {
        assertThat(selects(work -> work.rates().table())).hasSize(1);
    }

    @Test
    void theLedgerLoadsWithoutAQueryAPosting() {
        assertThat(selects(work -> work.ledger().ledger())).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void theFleetRegisterDoesNotQueryPerLease() {
        // One for the cars and their retirements, then the leases the cars actually have, loaded
        // a batch at a time. None of them has any, so this stays small.
        assertThat(selects(work -> work.fleet().register())).hasSizeLessThanOrEqualTo(2 * CARS + 2);
    }

    @Test
    void aCarLookupIsOneQuery() {
        assertThat(selects(work -> work.fleet().get(CarMark.parse("CN 400100"))))
                .hasSize(1);
    }

    @Test
    void readingEveryCarIsOneQuery() {
        List<Car> found = new ArrayList<>();
        List<String> seen = selects(work -> found.addAll(work.fleet().all()));
        assertThat(found).hasSize(CARS);
        assertThat(seen).hasSize(1);
    }

    @Test
    void readingCarsByOwnerIsOneQuery() {
        assertThat(selects(work -> work.fleet().byOwner(CN))).hasSize(1);
    }

    @Test
    void theCounterWatchesWhatItSaysItDoes() {
        assertThat(selects(work -> {
                    work.events().log();
                    work.events().log();
                }))
                .hasSize(2);
    }
}
