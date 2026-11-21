package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.embargo.Embargo;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoError;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoRegister;
import io.github.rkaushikdev.drawbarline.embargo.Movement;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.EmbargoRow;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RepositoryEmbargoTest {

    private static final ReportingMark CN = new ReportingMark("CN");
    private static final LocalDate EFFECTIVE = LocalDate.of(2025, 1, 6);
    private static final String REASON = "bridge out at hermansville";

    private static Embargo anEmbargo() {
        return Embargo.builder(101, EFFECTIVE, REASON).station("HRM").build();
    }

    private static Embargo onTheFamily(int number, String family) {
        return Embargo.builder(number, EFFECTIVE, REASON)
                .commodityFamily(family)
                .build();
    }

    private static Database empty() {
        return TestDatabase.fresh();
    }

    private static Database issued() {
        return TestDatabase.seeded(work -> {
            work.embargoes().issue(anEmbargo());
            work.embargoes().issue(onTheFamily(102, "01"));
        });
    }

    @Test
    void theEmbargoTableIsOnTheMetadata() {
        assertThat(Database.modelTables()).contains("embargo");
    }

    @Test
    void aRowReprNamesTheNumberAndDate() {
        EmbargoRow row = new EmbargoRow(101, EFFECTIVE, "x", "HRM", null, null, null, false);
        assertThat(row).hasToString("<EmbargoRow 101 from 2025-01-06>");
    }

    @Test
    void anEmbargoGoesInAndComesBackWhole() {
        try (UnitOfWork work = issued().unitOfWork()) {
            assertThat(work.embargoes().get(101)).isEqualTo(anEmbargo());
        }
    }

    @Test
    void aCommodityEmbargoKeepsItsFamily() {
        try (UnitOfWork work = issued().unitOfWork()) {
            assertThat(work.embargoes().get(102).commodityFamily()).contains("01");
        }
    }

    @Test
    void aRoadEmbargoKeepsItsRoad() {
        Database books = empty();
        try (UnitOfWork work = books.unitOfWork()) {
            work.embargoes()
                    .issue(Embargo.builder(103, EFFECTIVE, REASON).road(CN).build());
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.embargoes().get(103).road()).contains(CN);
        }
    }

    @Test
    void theSameNumberTwiceIsRefused() {
        try (UnitOfWork work = issued().unitOfWork()) {
            assertThatThrownBy(() -> work.embargoes().issue(anEmbargo())).isInstanceOf(EmbargoError.class);
        }
    }

    @Test
    void anEmbargoNobodyIssuedIsRefused() {
        try (UnitOfWork work = issued().unitOfWork()) {
            assertThatThrownBy(() -> work.embargoes().get(999)).isInstanceOf(EmbargoError.class);
        }
    }

    @Test
    void anEmbargoThatRestrictsNothingIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session()
                                .persist(new EmbargoRow(104, EFFECTIVE, "everything", null, null, null, null, false));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void anEmbargoThatExpiresFirstIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session()
                                .persist(new EmbargoRow(
                                        104, LocalDate.of(2025, 6, 6), "x", "HRM", null, null, EFFECTIVE, false));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void liftingAnEmbargoEndsIt() {
        Database books = issued();
        try (UnitOfWork work = books.unitOfWork()) {
            work.embargoes().lift(101, LocalDate.of(2025, 1, 20));
            work.commit();
        }
        Embargo found;
        try (UnitOfWork work = books.unitOfWork()) {
            found = work.embargoes().get(101);
        }
        assertThat(found.expires()).contains(LocalDate.of(2025, 1, 20));
        assertThat(found.inForceOn(LocalDate.of(2025, 1, 21))).isFalse();
    }

    @Test
    void liftingBeforeItTookEffectIsRefused() {
        try (UnitOfWork work = issued().unitOfWork()) {
            assertThatThrownBy(() -> work.embargoes().lift(101, LocalDate.of(2025, 1, 1)))
                    .isInstanceOf(EmbargoError.class);
        }
    }

    @Test
    void liftingOneThatAlreadyHasAnEndIsRefused() {
        try (UnitOfWork work = issued().unitOfWork()) {
            work.embargoes().lift(101, LocalDate.of(2025, 1, 20));
            assertThatThrownBy(() -> work.embargoes().lift(101, LocalDate.of(2025, 1, 25)))
                    .isInstanceOf(EmbargoError.class);
        }
    }

    @Test
    void liftingSomethingNeverIssuedIsRefused() {
        try (UnitOfWork work = issued().unitOfWork()) {
            assertThatThrownBy(() -> work.embargoes().lift(999, LocalDate.of(2025, 1, 20)))
                    .isInstanceOf(EmbargoError.class);
        }
    }

    @Test
    void theLiveEmbargoesComeBack() {
        try (UnitOfWork work = issued().unitOfWork()) {
            assertThat(work.embargoes().inForceOn(LocalDate.of(2025, 1, 10)))
                    .extracting(Embargo::number)
                    .containsExactly(101, 102);
        }
    }

    @Test
    void anExpiredEmbargoIsNotLive() {
        Database books = issued();
        try (UnitOfWork work = books.unitOfWork()) {
            work.embargoes().lift(101, LocalDate.of(2025, 1, 8));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.embargoes().inForceOn(LocalDate.of(2025, 1, 10)))
                    .extracting(Embargo::number)
                    .containsExactly(102);
        }
    }

    @Test
    void theWholeRegisterLoadsBack() {
        EmbargoRegister register;
        try (UnitOfWork work = issued().unitOfWork()) {
            register = work.embargoes().register();
        }
        LocalDate day = LocalDate.of(2025, 1, 10);
        assertThat(register.against(day, Movement.anywhere().atStation("HRM").carrying(Commodity.WHEAT)))
                .extracting(Embargo::number)
                .containsExactly(101, 102);
        assertThat(register.against(day, Movement.anywhere().atStation("ESC").carrying(Commodity.KRAFT_PAPER)))
                .isEmpty();
    }

    @Test
    void theRepositoryCountsWhatItIssued() {
        try (UnitOfWork work = issued().unitOfWork()) {
            assertThat(work.embargoes().size()).isEqualTo(2);
        }
    }
}
