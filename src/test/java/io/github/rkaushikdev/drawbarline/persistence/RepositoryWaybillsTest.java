package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.persistence.models.WaybillRow;
import io.github.rkaushikdev.drawbarline.support.Cars;
import io.github.rkaushikdev.drawbarline.waybill.Waybill;
import io.github.rkaushikdev.drawbarline.waybill.WaybillError;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoryWaybillsTest {

    private static final Lading GRAIN = new Lading(Commodity.WHEAT, new Weight(198000));
    private static final CarMark CAR = CarMark.parse("CN 400100");
    private static final LocalDate DATED = LocalDate.of(2025, 1, 14);
    private static final List<String> ROUTE = List.of("PWR", "HRM", "BKR", "ESC");

    private Database books;

    @BeforeEach
    void twoCarsOnFile() {
        books = TestDatabase.seeded(work -> {
            work.fleet().add(Cars.aCar("CN 400100"));
            work.fleet().add(Cars.aCar("CN 400101"));
        });
    }

    private static Waybill aWaybill() {
        return aWaybill(41207);
    }

    private static Waybill aWaybill(int number) {
        return aWaybill(number, CAR, DATED, Optional.of(GRAIN));
    }

    private static Waybill aWaybill(int number, CarMark car, LocalDate dated, Optional<Lading> lading) {
        return new Waybill(number, dated, car, "PWRGRN", "ESCPAP", ROUTE, lading);
    }

    private static Waybill anEmptyWaybill(int number) {
        return aWaybill(number, CAR, DATED, Optional.empty());
    }

    private void issued(Waybill... bills) {
        try (UnitOfWork work = books.unitOfWork()) {
            for (Waybill bill : bills) {
                work.waybills().issue(bill);
            }
            work.commit();
        }
    }

    private Waybill read(int number) {
        try (UnitOfWork work = books.unitOfWork()) {
            return work.waybills().get(number);
        }
    }

    @Test
    void theWaybillTablesAreOnTheMetadata() {
        assertThat(Database.modelTables()).contains("waybill", "route_stop");
    }

    @Test
    void aRowReprNamesTheNumberAndCar() {
        WaybillRow row = new WaybillRow(41207, DATED, CAR, "a", "b", null, null, null, false);
        assertThat(row).hasToString("<WaybillRow 41207 CN 400100>");
    }

    @Test
    void aWaybillGoesInAndComesBackWhole() {
        issued(aWaybill());
        assertThat(read(41207)).isEqualTo(aWaybill());
    }

    @Test
    void theRouteKeepsItsOrder() {
        issued(aWaybill());
        assertThat(read(41207).route()).containsExactly("PWR", "HRM", "BKR", "ESC");
    }

    @Test
    void aStoredWaybillStillMeasuresItsMileage() {
        issued(aWaybill());
        assertThat(read(41207).tenths()).isEqualTo(336);
    }

    @Test
    void anEmptyWaybillCarriesNoLading() {
        issued(anEmptyWaybill(41207));
        Waybill found = read(41207);
        assertThat(found.loaded()).isFalse();
        assertThat(found.lading()).isEmpty();
    }

    @Test
    void theLadingComesBackWithItsCommodity() {
        issued(aWaybill());
        Lading found = read(41207).lading().orElseThrow();
        assertThat(found.commodity().name()).isEqualTo("wheat");
        assertThat(found.commodity().bulk()).isTrue();
        assertThat(found.weight()).isEqualTo(new Weight(198000));
    }

    @Test
    void theSameNumberTwiceIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.waybills().issue(aWaybill());
            assertThatThrownBy(() -> work.waybills().issue(aWaybill())).isInstanceOf(WaybillError.class);
        }
    }

    @Test
    void aWaybillForACarNobodyFiledIsRefused() {
        Waybill nobody = aWaybill(41207, CarMark.parse("WC 000001"), DATED, Optional.of(GRAIN));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.waybills().issue(nobody)).isInstanceOf(UnknownCar.class);
        }
    }

    @Test
    void aWaybillNobodyIssuedIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.waybills().get(99999)).isInstanceOf(WaybillError.class);
        }
    }

    @Test
    void waybillsComeBackForOneCar() {
        issued(aWaybill(), aWaybill(41208, CarMark.parse("CN 400101"), DATED, Optional.of(GRAIN)));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.waybills().forCar(CAR)).extracting(Waybill::number).containsExactly(41207);
        }
    }

    @Test
    void waybillsComeBackForASpanOfDays() {
        issued(aWaybill(), aWaybill(41208, CAR, LocalDate.of(2025, 2, 3), Optional.of(GRAIN)));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.waybills().datedBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
                    .extracting(Waybill::number)
                    .containsExactly(41207);
        }
    }

    @Test
    void loadedWaybillsComeBackOnTheirOwn() {
        issued(aWaybill(), anEmptyWaybill(41208));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.waybills().loadedOnly()).extracting(Waybill::number).containsExactly(41207);
        }
    }

    @Test
    void aDiversionIsASecondWaybill() {
        try (UnitOfWork work = books.unitOfWork()) {
            Waybill first = work.waybills().issue(aWaybill());
            work.waybills().issue(first.divert("HRM", "GRB", 41208));
            work.commit();
        }
        assertThat(read(41207).route()).containsExactly("PWR", "HRM", "BKR", "ESC");
        assertThat(read(41208).route()).containsExactly("PWR", "HRM", "GRB");
    }

    @Test
    void aWaybillNumberedBelowOneIsRefusedByTheTable() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new WaybillRow(0, DATED, CAR, "a", "b", null, null, null, false));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void theRepositoryCountsWhatItIssued() {
        issued(aWaybill(), aWaybill(41208));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.waybills().size()).isEqualTo(2);
        }
    }
}
