package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.carhire.Rate;
import io.github.rkaushikdev.drawbarline.carhire.RateTable;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.persistence.models.RateBandRow;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryRatesTest {

    private static final CarTypeCode HOPPER = new CarTypeCode("L351");
    private static final CarTypeCode BOX = new CarTypeCode("X202");
    private static final LocalDate JULY_2024 = LocalDate.of(2024, 7, 1);

    private static List<Band> someBands() {
        return List.of(
                new Band(HOPPER, 0, 9, JULY_2024, new Rate(4630, 1210)),
                new Band(HOPPER, 10, 19, JULY_2024, new Rate(3180, 1210)),
                new Band(HOPPER, 0, 9, LocalDate.of(2025, 7, 1), new Rate(4880, 1290)),
                new Band(BOX, 0, 99, JULY_2024, new Rate(2240, 980)));
    }

    private static Database empty() {
        return TestDatabase.fresh();
    }

    private static Database filed() {
        return TestDatabase.seeded(work -> work.rates().fileAll(someBands()));
    }

    @Test
    void theBandTableIsOnTheMetadata() {
        assertThat(Database.modelTables()).contains("car_hire_band");
    }

    @Test
    void aRowReprNamesTheTypeAgesAndDate() {
        RateBandRow row = new RateBandRow(HOPPER, 0, 9, JULY_2024, 1, 1);
        assertThat(row).hasToString("<RateBandRow L351 0-9 2024-07-01>");
    }

    @Test
    void aBandGoesInAndComesBackWhole() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThat(work.rates().bandsFor(BOX))
                    .containsExactly(new Band(BOX, 0, 99, JULY_2024, new Rate(2240, 980)));
        }
    }

    @Test
    void filingTheSameBandTwiceIsRefused() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThatThrownBy(() -> work.rates().file(new Band(HOPPER, 0, 9, JULY_2024, new Rate(1, 1))))
                    .isInstanceOf(RateError.class);
        }
    }

    @Test
    void theTableRefusesADuplicateTheRepositoryDidNotSee() {
        Band band = new Band(HOPPER, 0, 9, JULY_2024, new Rate(4630, 1210));
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(RateMappers.rowFromBand(band));
                        work.session().persist(RateMappers.rowFromBand(band));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void aBandThatRunsBackwardsIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new RateBandRow(HOPPER, 19, 10, JULY_2024, 1, 1));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void aNegativeRateIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new RateBandRow(HOPPER, 0, 9, JULY_2024, -1, 1));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void aRateTooLargeForItsColumnIsRefusedRatherThanCut() {
        Band band = new Band(HOPPER, 0, 9, JULY_2024, new Rate(Integer.MAX_VALUE + 1L, 1210));
        assertThatThrownBy(() -> RateMappers.rowFromBand(band))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("hourly_tenths");
    }

    @Test
    void bandsComeBackInDateAndAgeOrder() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThat(work.rates().bandsFor(HOPPER))
                    .extracting(Band::effective, Band::fromAge)
                    .containsExactly(tuple(JULY_2024, 0), tuple(JULY_2024, 10), tuple(LocalDate.of(2025, 7, 1), 0));
        }
    }

    @Test
    void aTypeWithNoBandsComesBackEmpty() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThat(work.rates().bandsFor(new CarTypeCode("T104"))).isEmpty();
        }
    }

    @Test
    void theEffectiveDatesComeBackOnceEach() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThat(work.rates().effectiveDates(HOPPER)).containsExactly(JULY_2024, LocalDate.of(2025, 7, 1));
        }
    }

    @Test
    void theWholeScheduleLoadsIntoTheDomainTable() {
        RateTable table;
        try (UnitOfWork work = filed().unitOfWork()) {
            table = work.rates().table();
        }
        assertThat(table.lookup(HOPPER, 3, LocalDate.of(2025, 1, 14))).isEqualTo(new Rate(4630, 1210));
        assertThat(table.lookup(HOPPER, 3, LocalDate.of(2025, 7, 1))).isEqualTo(new Rate(4880, 1290));
        assertThat(table.lookup(BOX, 40, LocalDate.of(2025, 1, 14))).isEqualTo(new Rate(2240, 980));
    }

    @Test
    void aLoadedScheduleReportsWhereTheRateSteps() {
        RateTable table;
        try (UnitOfWork work = filed().unitOfWork()) {
            table = work.rates().table();
        }
        assertThat(table.changesBetween(HOPPER, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 8, 1)))
                .containsExactly(LocalDate.of(2025, 7, 1));
    }

    @Test
    void theRepositoryCountsItsBands() {
        try (UnitOfWork work = filed().unitOfWork()) {
            assertThat(work.rates().size()).isEqualTo(4);
        }
    }

    @Test
    void anEmptyScheduleLoadsAsAnEmptyTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThat(work.rates().table().size()).isZero();
        }
    }
}
