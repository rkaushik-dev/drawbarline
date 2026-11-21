package io.github.rkaushikdev.drawbarline.persistence;

import static io.github.rkaushikdev.drawbarline.persistence.FleetMappers.carFromRow;
import static io.github.rkaushikdev.drawbarline.persistence.FleetMappers.leaseRowsFor;
import static io.github.rkaushikdev.drawbarline.persistence.FleetMappers.ownershipFromRows;
import static io.github.rkaushikdev.drawbarline.persistence.FleetMappers.rowFromCar;
import static io.github.rkaushikdev.drawbarline.persistence.FleetMappers.updateRowFromCar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.RegistryError;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.fleet.Ownership;
import io.github.rkaushikdev.drawbarline.fleet.OwnershipError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.persistence.models.CarRow;
import io.github.rkaushikdev.drawbarline.persistence.models.LeaseRow;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappersFleetTest {

    private static final ReportingMark DBL = new ReportingMark("DBL");
    private static final ReportingMark CN = new ReportingMark("CN");
    private static final ReportingMark ELS = new ReportingMark("ELS");
    private static final String MARK = "DBL 104207";

    private static Car aCar() {
        return aCar(MARK);
    }

    private static Car aCar(String mark) {
        return aCar(mark, new Weight(63500));
    }

    private static Car aCar(String mark, Weight lightWeight) {
        return new Car(
                CarMark.parse(mark),
                new MechanicalDesignation("LO"),
                new CarTypeCode("L351"),
                LocalDate.of(2020, 6, 1),
                lightWeight,
                new Weight(286000),
                Length.of(59, 6));
    }

    private static CarRow stored(Database books) {
        return books.fromTransaction(session -> {
            CarRow row = session.find(CarRow.class, MARK);
            row.getLeases().size();
            return row;
        });
    }

    @Test
    void aCarMapsToARowAndBack() {
        assertThat(carFromRow(rowFromCar(aCar()))).isEqualTo(aCar());
    }

    @Test
    void aRebuiltCarKeepsItsRebuildDate() {
        Car built = aCar();
        Car car = new Car(
                built.mark(),
                built.designation(),
                built.typeCode(),
                built.built(),
                built.lightWeight(),
                built.grossRailLoad(),
                built.outsideLength(),
                LocalDate.of(2022, 9, 1));
        assertThat(carFromRow(rowFromCar(car)).rebuilt()).contains(LocalDate.of(2022, 9, 1));
    }

    @Test
    void theOwnerIsWrittenAsItsOwnColumn() {
        assertThat(rowFromCar(aCar()).getOwner()).isEqualTo(DBL);
    }

    @Test
    void aPrivateCarWritesItsPrivateMarkAsTheOwner() {
        assertThat(rowFromCar(aCar("PLMX 9001")).getOwner()).isEqualTo(new ReportingMark("PLMX"));
    }

    @Test
    void aCarSurvivesATripThroughTheDatabase() {
        Database books = TestDatabase.fresh();
        books.inTransaction(session -> session.persist(rowFromCar(aCar())));
        assertThat(carFromRow(stored(books))).isEqualTo(aCar());
    }

    @Test
    void aCorrectionWritesOntoTheRowInTheSession() {
        Database books = TestDatabase.fresh();
        books.inTransaction(session -> session.persist(rowFromCar(aCar())));
        books.inTransaction(
                session -> updateRowFromCar(session.find(CarRow.class, MARK), aCar(MARK, new Weight(64200))));
        assertThat(stored(books).getLightWeight()).isEqualTo(new Weight(64200));
    }

    @Test
    void aCorrectionCannotChangeWhichCarItIs() {
        CarRow row = rowFromCar(aCar());
        assertThatThrownBy(() -> updateRowFromCar(row, aCar("DBL 104208"))).isInstanceOf(RegistryError.class);
    }

    @Test
    void anUnleasedCarMakesNoLeaseRows() {
        assertThat(leaseRowsFor(CarMark.parse(MARK), new Ownership(DBL))).isEmpty();
    }

    @Test
    void everyTermBecomesARow() {
        Ownership ownership = new Ownership(DBL);
        ownership.lease(CN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        ownership.lease(ELS, LocalDate.of(2025, 7, 1));
        assertThat(leaseRowsFor(CarMark.parse(MARK), ownership))
                .extracting(LeaseRow::getLessee)
                .containsExactly(CN, ELS);
    }

    @Test
    void rowsReplayIntoTheSameOwnership() {
        Ownership ownership = new Ownership(DBL);
        ownership.lease(CN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        ownership.lease(ELS, LocalDate.of(2025, 7, 1));
        Ownership rebuilt = ownershipFromRows(DBL, leaseRowsFor(CarMark.parse(MARK), ownership));
        assertThat(rebuilt.holderOn(LocalDate.of(2025, 3, 1))).isEqualTo(CN);
        assertThat(rebuilt.holderOn(LocalDate.of(2025, 8, 1))).isEqualTo(ELS);
    }

    @Test
    void rowsOutOfOrderStillReplay() {
        List<LeaseRow> rows = List.of(
                new LeaseRow(ELS, LocalDate.of(2025, 7, 1), null),
                new LeaseRow(CN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30)));
        assertThat(ownershipFromRows(DBL, rows).holderOn(LocalDate.of(2025, 3, 1)))
                .isEqualTo(CN);
    }

    @Test
    void rowsThatOverlapAreRefusedAtTheRead() {
        List<LeaseRow> rows = List.of(
                new LeaseRow(CN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30)),
                new LeaseRow(ELS, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 12, 31)));
        assertThatThrownBy(() -> ownershipFromRows(DBL, rows)).isInstanceOf(OwnershipError.class);
    }

    @Test
    void ownershipSurvivesATripThroughTheDatabase() {
        Ownership ownership = new Ownership(DBL);
        ownership.lease(CN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        Database books = TestDatabase.fresh();
        books.inTransaction(session -> {
            CarRow row = rowFromCar(aCar());
            leaseRowsFor(row.getMark(), ownership).forEach(row::addLease);
            session.persist(row);
        });
        CarRow row = stored(books);
        Ownership rebuilt = ownershipFromRows(row.getOwner(), row.getLeases());
        assertThat(rebuilt.holderOn(LocalDate.of(2025, 3, 1))).isEqualTo(CN);
        assertThat(rebuilt.holderOn(LocalDate.of(2025, 8, 1))).isEqualTo(DBL);
    }
}
