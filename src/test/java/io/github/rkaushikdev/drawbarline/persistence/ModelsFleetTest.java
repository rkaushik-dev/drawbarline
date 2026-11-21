package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.persistence.models.CarRow;
import io.github.rkaushikdev.drawbarline.persistence.models.LeaseRow;
import io.github.rkaushikdev.drawbarline.persistence.models.RetirementRow;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsFleetTest {

    private static final String MARK = "DBL 104207";

    private Database books;

    @BeforeEach
    void freshBooks() {
        books = TestDatabase.fresh();
    }

    private static CarRow aRow() {
        return aRow(MARK, new Weight(63500), Length.of(59, 6));
    }

    private static CarRow aRow(String mark) {
        return aRow(mark, new Weight(63500), Length.of(59, 6));
    }

    private static CarRow aRow(String mark, Weight lightWeight, Length length) {
        CarMark car = CarMark.parse(mark);
        return new CarRow(
                car,
                car.mark(),
                new MechanicalDesignation("LO"),
                new CarTypeCode("L351"),
                LocalDate.of(2020, 6, 1),
                null,
                lightWeight,
                new Weight(286000),
                length);
    }

    private CarRow found() {
        return books.fromTransaction(session -> {
            CarRow row = session.find(CarRow.class, MARK);
            row.getLeases().size();
            row.getRetirement().ifPresent(RetirementRow::getReason);
            return row;
        });
    }

    @Test
    void theFleetTablesAreOnTheMetadata() {
        assertThat(Database.modelTables()).contains("car", "lease", "retirement");
    }

    @Test
    void aCarGoesInAndComesBackAsDomainObjects() {
        books.inTransaction(session -> session.persist(aRow()));
        CarRow found = found();
        assertThat(found.getMark()).isEqualTo(CarMark.parse(MARK));
        assertThat(found.getTypeCode()).isEqualTo(new CarTypeCode("L351"));
        assertThat(found.getLightWeight()).isEqualTo(new Weight(63500));
        assertThat(found.getOutsideLength()).isEqualTo(Length.of(59, 6));
    }

    @Test
    void aCarReprNamesItsMarks() {
        assertThat(aRow()).hasToString("<CarRow DBL 104207>");
    }

    @Test
    void theSameCarTwiceIsRefused() {
        books.inTransaction(session -> session.persist(aRow()));
        assertThatThrownBy(() -> books.inTransaction(session -> session.persist(aRow())))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aCarThatWeighsItsGrossRailLoadIsRefusedByTheTable() {
        assertThatThrownBy(() -> books.inTransaction(
                        session -> session.persist(aRow(MARK, new Weight(286000), Length.of(59, 6)))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aCarOfNoLengthIsRefusedByTheTable() {
        assertThatThrownBy(() ->
                        books.inTransaction(session -> session.persist(aRow(MARK, new Weight(63500), new Length(0)))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aRebuildDateIsOptional() {
        books.inTransaction(session -> session.persist(aRow()));
        assertThat(found().getRebuilt()).isEmpty();
    }

    @Test
    void aLeaseHangsOffItsCar() {
        books.inTransaction(session -> {
            CarRow car = aRow();
            car.addLease(new LeaseRow(new ReportingMark("CN"), LocalDate.of(2025, 1, 1), null));
            session.persist(car);
        });
        assertThat(found().getLeases()).extracting(LeaseRow::getLessee).containsExactly(new ReportingMark("CN"));
    }

    @Test
    void aLeaseAgainstACarNobodyFiledIsRefused() {
        assertThatThrownBy(() -> books.inTransaction(session -> session.persist(new LeaseRow(
                        CarMark.parse("WC 000001"), new ReportingMark("CN"), LocalDate.of(2025, 1, 1), null))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aLeaseThatEndsBeforeItBeginsIsRefusedByTheTable() {
        assertThatThrownBy(() -> books.inTransaction(session -> {
                    CarRow car = aRow();
                    car.addLease(
                            new LeaseRow(new ReportingMark("CN"), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1)));
                    session.persist(car);
                }))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void twoLeasesStartingTheSameDayAreRefused() {
        assertThatThrownBy(() -> books.inTransaction(session -> {
                    CarRow car = aRow();
                    car.addLease(new LeaseRow(new ReportingMark("CN"), LocalDate.of(2025, 1, 1), null));
                    car.addLease(new LeaseRow(new ReportingMark("ELS"), LocalDate.of(2025, 1, 1), null));
                    session.persist(car);
                }))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void leasesComeBackInTheOrderTheyRan() {
        books.inTransaction(session -> {
            CarRow car = aRow();
            car.addLease(new LeaseRow(new ReportingMark("CN"), LocalDate.of(2025, 7, 1), null));
            car.addLease(new LeaseRow(new ReportingMark("ELS"), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30)));
            session.persist(car);
        });
        assertThat(found().getLeases())
                .extracting(LeaseRow::getBegan)
                .containsExactly(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 7, 1));
    }

    @Test
    void aRetirementIsAtMostOneACar() {
        books.inTransaction(session -> {
            CarRow car = aRow();
            car.setRetirement(new RetirementRow(LocalDate.of(2025, 3, 31), "wreck at wallace"));
            session.persist(car);
        });
        assertThat(found().getRetirement()).map(RetirementRow::getReason).contains("wreck at wallace");
    }

    @Test
    void deletingACarTakesItsLeasesWithIt() {
        books.inTransaction(session -> {
            CarRow car = aRow();
            car.addLease(new LeaseRow(new ReportingMark("CN"), LocalDate.of(2025, 1, 1), null));
            session.persist(car);
        });
        books.inTransaction(session -> session.remove(session.find(CarRow.class, MARK)));
        List<LeaseRow> left = books.fromTransaction(session ->
                session.createQuery("select l from LeaseRow l", LeaseRow.class).getResultList());
        assertThat(left).isEmpty();
    }

    @Test
    void carsCanBeFoundByOwner() {
        books.inTransaction(session -> {
            session.persist(aRow());
            session.persist(aRow("DBL 104208"));
            session.persist(aRow("CN 400100"));
        });
        List<CarRow> found = books.fromTransaction(
                session -> session.createQuery("select c from CarRow c where c.owner = :owner", CarRow.class)
                        .setParameter("owner", new ReportingMark("DBL"))
                        .getResultList());
        assertThat(found).hasSize(2);
    }
}
