package io.github.rkaushikdev.drawbarline.persistence;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.persistence.Counting.countOf;
import static io.github.rkaushikdev.drawbarline.persistence.Counting.countWhere;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.EventRow;
import io.github.rkaushikdev.drawbarline.support.Cars;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CountingTest {

    private static final ReportingMark CN = new ReportingMark("CN");
    private static final int CARS = 6;

    static Database stocked(int cars) {
        return TestDatabase.seeded(work -> {
            for (int at = 0; at < cars; at++) {
                String mark = "CN " + (400100 + at);
                work.fleet().add(Cars.aCar(mark));
                work.events()
                        .record(Event.builder(EventKind.RECEIVED, CarMark.parse(mark), utc(2025, 1, 6, 12), "ESC")
                                .road(CN)
                                .build());
            }
        });
    }

    private static Counting.Condition<EventRow> kindIs(EventKind kind) {
        return (where, row) -> where.equal(row.get("kind"), kind.name());
    }

    @Test
    void aCountComesBackAsANumber() {
        try (UnitOfWork work = stocked(CARS).unitOfWork()) {
            assertThat(countOf(work.session(), EventRow.class)).isEqualTo(CARS);
        }
    }

    @Test
    void anEmptyTableCountsNothing() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            assertThat(countOf(work.session(), EventRow.class)).isZero();
        }
    }

    @Test
    void aCountDoesNotReadTheRows() {
        Database books = stocked(CARS);
        try (UnitOfWork work = books.unitOfWork();
                Statements.Watch seen = books.statements().watch()) {
            countOf(work.session(), EventRow.class);
            assertThat(seen.statements()).hasSize(1);
            assertThat(seen.statements().get(0).toLowerCase(Locale.ROOT)).contains("count(");
        }
    }

    @Test
    void aFilteredCountMatches() {
        try (UnitOfWork work = stocked(CARS).unitOfWork()) {
            assertThat(countWhere(work.session(), EventRow.class, kindIs(EventKind.RECEIVED)))
                    .isEqualTo(CARS);
        }
    }

    @Test
    void aFilterThatMatchesNothingCountsNothing() {
        try (UnitOfWork work = stocked(CARS).unitOfWork()) {
            assertThat(countWhere(work.session(), EventRow.class, kindIs(EventKind.PULLED)))
                    .isZero();
        }
    }

    @Test
    void twoFiltersAreTakenTogether() {
        try (UnitOfWork work = stocked(CARS).unitOfWork()) {
            int found = countWhere(
                    work.session(),
                    EventRow.class,
                    kindIs(EventKind.RECEIVED),
                    (where, row) -> where.equal(row.get("station"), "ESC"));
            assertThat(found).isEqualTo(CARS);
            int none = countWhere(
                    work.session(),
                    EventRow.class,
                    kindIs(EventKind.RECEIVED),
                    (where, row) -> where.equal(row.get("station"), "PWR"));
            assertThat(none).isZero();
        }
    }

    @Test
    void theRepositoriesAgreeWithTheCount() {
        try (UnitOfWork work = stocked(CARS).unitOfWork()) {
            assertThat(work.events().size()).isEqualTo(CARS);
            assertThat(work.fleet().size()).isEqualTo(CARS);
        }
    }

    @Test
    void countingTheFleetDoesNotReadTheCars() {
        Database books = stocked(CARS);
        try (UnitOfWork work = books.unitOfWork();
                Statements.Watch seen = books.statements().watch()) {
            work.fleet().size();
            assertThat(seen.count()).isEqualTo(1);
        }
    }
}
