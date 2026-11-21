package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.customers.Shipper;
import io.github.rkaushikdev.drawbarline.distribution.CarOrder;
import io.github.rkaushikdev.drawbarline.distribution.DistributionError;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.persistence.models.CarOrderRow;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoryOrdersTest {

    private static final Shipper MILL = new Shipper("ESCPAP", "Escanaba Paper");
    private static final Shipper ELEVATOR = new Shipper("PWRGRN", "Powers Grain");
    private static final LocalDate WANTED = LocalDate.of(2025, 1, 14);
    private static final LocalDate PLACED = LocalDate.of(2025, 1, 10);

    private Database books;

    @BeforeEach
    void twoShippersOnFile() {
        books = TestDatabase.seeded(work -> {
            work.customers().addShipper(MILL);
            work.customers().addShipper(ELEVATOR);
            work.customers().addIndustry(new Industry("ESCPAP1", "mill house", "ESC", MILL, 11));
            work.customers().addIndustry(new Industry("PWRGRN1", "elevator", "PWR", ELEVATOR, 20));
        });
    }

    private static CarOrder anOrder() {
        return anOrder(1, WANTED);
    }

    private static CarOrder anOrder(int number, LocalDate wantedOn) {
        return anOrder(number, "ESCPAP", "ESCPAP1", wantedOn);
    }

    private static CarOrder anOrder(int number, String shipper, String industry, LocalDate wantedOn) {
        return new CarOrder(number, shipper, industry, CarKind.COVERED_HOPPER, 4, wantedOn);
    }

    private void placed() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.orders().place(anOrder(), PLACED);
            work.commit();
        }
    }

    @Test
    void theOrderTableIsOnTheMetadata() {
        assertThat(Database.modelTables()).contains("car_order");
    }

    @Test
    void aRowReprNamesTheOrderAndShipper() {
        CarOrderRow row = new CarOrderRow(1, "ESCPAP", "ESCPAP1", "L", 4, WANTED, PLACED);
        assertThat(row).hasToString("<CarOrderRow 1 ESCPAP 4>");
    }

    @Test
    void anOrderGoesInAndComesBackWhole() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().get(1)).isEqualTo(anOrder());
        }
    }

    @Test
    void theKindSurvivesTheTrip() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().get(1).kind()).isSameAs(CarKind.COVERED_HOPPER);
        }
    }

    @Test
    void theSameNumberTwiceIsRefused() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.orders().place(anOrder(), PLACED)).isInstanceOf(DistributionError.class);
        }
    }

    @Test
    void anOrderForAShipperNobodyFiledIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.orders().place(anOrder(1, "NOBODY", "ESCPAP1", WANTED), PLACED))
                    .isInstanceOf(DistributionError.class);
        }
    }

    @Test
    void anOrderForATrackNobodyFiledIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.orders().place(anOrder(1, "ESCPAP", "NOWHERE", WANTED), PLACED))
                    .isInstanceOf(DistributionError.class);
        }
    }

    @Test
    void anOrderPlacedAfterTheCarsAreWantedIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.orders().place(anOrder(), LocalDate.of(2025, 1, 20)))
                    .isInstanceOf(DistributionError.class);
        }
    }

    @Test
    void anOrderPlacedOnTheDayItIsWantedIsAllowed() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.orders().place(anOrder(), WANTED);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().size()).isEqualTo(1);
        }
    }

    @Test
    void anOrderNobodyPlacedIsRefused() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.orders().get(99)).isInstanceOf(DistributionError.class);
        }
    }

    @Test
    void anOpenOrderShowsUpOnItsDay() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().openOn(LocalDate.of(2025, 1, 12)))
                    .extracting(CarOrder::number)
                    .containsExactly(1);
        }
    }

    @Test
    void anOrderIsNotOpenBeforeItWasPlaced() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().openOn(LocalDate.of(2025, 1, 9))).isEmpty();
        }
    }

    @Test
    void anOrderIsNotOpenAfterTheDayItWanted() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().openOn(LocalDate.of(2025, 1, 15))).isEmpty();
        }
    }

    @Test
    void openOrdersComeBackOldestFirst() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.orders().place(anOrder(1, LocalDate.of(2025, 1, 20)), PLACED);
            work.orders().place(anOrder(2, LocalDate.of(2025, 1, 12)), PLACED);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().openOn(LocalDate.of(2025, 1, 11)))
                    .extracting(CarOrder::number)
                    .containsExactly(2, 1);
        }
    }

    @Test
    void aCancelledOrderIsNotOpen() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            work.orders().cancel(1, LocalDate.of(2025, 1, 12));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().openOn(LocalDate.of(2025, 1, 13))).isEmpty();
            assertThat(work.orders().cancelledOn(1)).contains(LocalDate.of(2025, 1, 12));
        }
    }

    @Test
    void cancellingTwiceIsRefused() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            work.orders().cancel(1, LocalDate.of(2025, 1, 12));
            assertThatThrownBy(() -> work.orders().cancel(1, LocalDate.of(2025, 1, 13)))
                    .isInstanceOf(DistributionError.class);
        }
    }

    @Test
    void cancellingBeforeItWasPlacedIsRefused() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.orders().cancel(1, LocalDate.of(2025, 1, 1)))
                    .isInstanceOf(DistributionError.class);
        }
    }

    @Test
    void cancellingSomethingNeverPlacedIsRefused() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.orders().cancel(99, LocalDate.of(2025, 1, 12)))
                    .isInstanceOf(DistributionError.class);
        }
    }

    @Test
    void anOpenOrderHasNoCancellationDate() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().cancelledOn(1)).isEmpty();
        }
    }

    @Test
    void ordersComeBackForOneShipper() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.orders().place(anOrder(1, WANTED), PLACED);
            work.orders().place(anOrder(2, "PWRGRN", "PWRGRN1", WANTED), PLACED);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().forShipper("PWRGRN"))
                    .extracting(CarOrder::number)
                    .containsExactly(2);
        }
    }

    @Test
    void anOrderForNoCarsIsRefusedByTheTable() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new CarOrderRow(9, "ESCPAP", "ESCPAP1", "L", 0, WANTED, PLACED));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void theStationsComeBackForTheDistribution() {
        placed();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.orders().stations()).isEqualTo(Map.of("ESCPAP1", "ESC", "PWRGRN1", "PWR"));
        }
    }
}
