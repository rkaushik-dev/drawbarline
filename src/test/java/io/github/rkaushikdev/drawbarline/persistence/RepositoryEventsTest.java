package io.github.rkaushikdev.drawbarline.persistence;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.CarState;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.Replay;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.EventRow;
import io.github.rkaushikdev.drawbarline.support.Cars;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoryEventsTest {

    private static final CarMark CAR = CarMark.parse("CN 400100");
    private static final CarMark OTHER = CarMark.parse("DBL 104207");
    private static final CarMark NOBODY = CarMark.parse("WC 000001");
    private static final ReportingMark CN = new ReportingMark("CN");

    private Database books;

    @BeforeEach
    void twoCarsOnFile() {
        books = TestDatabase.seeded(work -> {
            work.fleet().add(Cars.aCar("CN 400100"));
            work.fleet().add(Cars.aCar("DBL 104207"));
        });
    }

    private static Event received() {
        return received(CAR);
    }

    private static Event received(CarMark car) {
        return Event.builder(EventKind.RECEIVED, car, utc(2025, 1, 6, 12), "ESC")
                .road(CN)
                .waybill(41207)
                .build();
    }

    private static Event delivered() {
        return Event.builder(EventKind.DELIVERED, CAR, utc(2025, 1, 9, 12), "MRN")
                .road(CN)
                .build();
    }

    private static Event moved() {
        return Event.builder(EventKind.MOVED, CAR, utc(2025, 1, 7, 18), "PWR")
                .tenths(336)
                .build();
    }

    @Test
    void theEventTableIsOnTheMetadata() {
        assertThat(Database.modelTables()).contains("car_event");
    }

    @Test
    void anEventGoesInAndComesBackWhole() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().record(received());
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().stream(CAR).events()).containsExactly(received());
        }
    }

    @Test
    void aRowReprNamesTheCarAndTheDay() {
        EventRow row = new EventRow(CAR, EventKind.RECEIVED, utc(2025, 1, 6, 12), "ESC", null, null, null, null, "");
        assertThat(row).hasToString("<EventRow CN 400100 RECEIVED 2025-01-06>");
    }

    @Test
    void theSameEventTwiceIsRefusedByTheRepository() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().record(received());
            assertThatThrownBy(() -> work.events().record(received())).isInstanceOf(EventError.class);
        }
    }

    @Test
    void theTableRefusesADuplicateTheRepositoryDidNotSee() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(EventMappers.rowFromEvent(received()));
                        work.session().persist(EventMappers.rowFromEvent(received()));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void theTableRefusesAnEventTheRepositoryDidNotSee() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(EventMappers.rowFromEvent(received(NOBODY)));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void aStreamComesBackInTheOrderItHappened() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().record(delivered());
            work.events().record(received());
            work.events().record(moved());
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().stream(CAR).events())
                    .extracting(Event::kind)
                    .containsExactly(EventKind.RECEIVED, EventKind.MOVED, EventKind.DELIVERED);
        }
    }

    @Test
    void aStoredStreamReplaysTheSameWay() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().recordAll(List.of(received(), moved(), delivered()));
            work.commit();
        }
        CarState state;
        try (UnitOfWork work = books.unitOfWork()) {
            state = Replay.replay(work.events().stream(CAR));
        }
        assertThat(state.onLine()).isFalse();
        assertThat(state.tenthsRun()).isEqualTo(336);
    }

    @Test
    void aCarWithNothingRecordedReadsAsAnEmptyStream() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().stream(CAR).size()).isZero();
        }
    }

    @Test
    void eventsComeBackForAWindow() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().record(received());
            work.events()
                    .record(Event.builder(EventKind.DELIVERED, CAR, utc(2025, 2, 9, 12), "MRN")
                            .road(CN)
                            .build());
            work.commit();
        }
        Interval january = new Interval(utc(2025, 1, 1), utc(2025, 2, 1));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().during(january)).extracting(Event::kind).containsExactly(EventKind.RECEIVED);
        }
    }

    @Test
    void eventsComeBackByKind() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().record(received());
            work.events().record(received(OTHER));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().ofKind(EventKind.RECEIVED)).hasSize(2);
            assertThat(work.events().ofKind(EventKind.PULLED)).isEmpty();
        }
    }

    @Test
    void theCarsWithEventsComeBackInOrder() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().record(received(OTHER));
            work.events().record(received());
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().cars()).containsExactly(CAR, OTHER);
        }
    }

    @Test
    void theLastMomentIsTheLatestEvent() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().recordAll(List.of(received(), delivered()));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().lastMoment()).contains(utc(2025, 1, 9, 12));
        }
    }

    @Test
    void anEmptyTableHasNoLastMoment() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().lastMoment()).isEmpty();
        }
    }

    @Test
    void theRepositoryCountsItsRows() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().recordAll(List.of(received(), moved(), delivered()));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.events().size()).isEqualTo(3);
        }
    }

    @Test
    void theRepositoryBuildsTheInMemoryLog() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.events().recordAll(List.of(received(), moved(), delivered()));
            work.events().record(received(OTHER));
            work.commit();
        }
        EventLog log;
        try (UnitOfWork work = books.unitOfWork()) {
            log = work.events().log();
        }
        assertThat(log.cars()).containsExactly(CAR, OTHER);
        assertThat(log.size()).isEqualTo(4);
    }

    @Test
    void aMovementOfNoDistanceIsRefusedByTheTable() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session()
                                .persist(new EventRow(
                                        CAR, EventKind.MOVED, utc(2025, 1, 7, 18), "PWR", null, null, null, 0, ""));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void anEventAgainstACarWithNoRecordIsRefusedBeforeTheTable() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.events().record(received(NOBODY))).isInstanceOf(UnknownCar.class);
        }
    }
}
