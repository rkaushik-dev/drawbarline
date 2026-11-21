package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.DuplicateCar;
import io.github.rkaushikdev.drawbarline.RegistryError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.fleet.Ownership;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RepositoryFleetTest {

    private static final ReportingMark DBL = new ReportingMark("DBL");
    private static final ReportingMark CN = new ReportingMark("CN");
    private static final ReportingMark ELS = new ReportingMark("ELS");
    private static final CarMark FIRST = CarMark.parse("DBL 104207");

    static Car aCar() {
        return aCar("DBL 104207");
    }

    static Car aCar(String mark) {
        return aCar(mark, new Weight(63500));
    }

    static Car aCar(String mark, Weight lightWeight) {
        return new Car(
                CarMark.parse(mark),
                new MechanicalDesignation("LO"),
                new CarTypeCode("L351"),
                LocalDate.of(2020, 6, 1),
                lightWeight,
                new Weight(286000),
                Length.of(59, 6));
    }

    private static Database empty() {
        return TestDatabase.fresh();
    }

    private static Database stocked() {
        return TestDatabase.seeded(work -> {
            for (String mark : new String[] {"DBL 104207", "DBL 104208", "CN 400100"}) {
                work.fleet().add(aCar(mark));
            }
        });
    }

    @Test
    void aCarAddedAndCommittedComesBack() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.fleet().get(FIRST)).isEqualTo(aCar());
        }
    }

    @Test
    void aCarAddedWithoutACommitIsNotThere() {
        Database books = empty();
        try (UnitOfWork work = books.unitOfWork()) {
            work.fleet().add(aCar());
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.fleet().has(FIRST)).isFalse();
        }
    }

    @Test
    void aBlockThatThrowsLeavesNothingBehind() {
        Database books = empty();
        assertThatThrownBy(() -> {
                    try (UnitOfWork work = books.unitOfWork()) {
                        work.fleet().add(aCar());
                        throw new IllegalStateException("something went wrong");
                    }
                })
                .isInstanceOf(IllegalStateException.class);
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.fleet().size()).isZero();
        }
    }

    @Test
    void theSameCarTwiceIsRefused() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.fleet().add(aCar())).isInstanceOf(DuplicateCar.class);
        }
    }

    @Test
    void aCarNobodyFiledIsUnknown() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.fleet().get(CarMark.parse("WC 1"))).isInstanceOf(UnknownCar.class);
        }
    }

    @Test
    void aCorrectionIsWritten() {
        Database books = stocked();
        try (UnitOfWork work = books.unitOfWork()) {
            work.fleet().replace(aCar("DBL 104207", new Weight(64200)));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.fleet().get(FIRST).lightWeight()).isEqualTo(new Weight(64200));
        }
    }

    @Test
    void correctingACarNobodyFiledIsRefused() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.fleet().replace(aCar("WC 1"))).isInstanceOf(UnknownCar.class);
        }
    }

    @Test
    void aNewCarIsOwedToItsOwner() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.fleet().ownership(CarMark.parse("CN 400100")).owner())
                    .isEqualTo(CN);
        }
    }

    @Test
    void leasesAreSavedAndReadBack() {
        Database books = stocked();
        try (UnitOfWork work = books.unitOfWork()) {
            Ownership ownership = work.fleet().ownership(FIRST);
            ownership.lease(CN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
            work.fleet().saveOwnership(FIRST, ownership);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.fleet().ownership(FIRST).holderOn(LocalDate.of(2025, 3, 1)))
                    .isEqualTo(CN);
        }
    }

    @Test
    void savingOwnershipReplacesWhatWasThere() {
        Database books = stocked();
        try (UnitOfWork work = books.unitOfWork()) {
            Ownership first = work.fleet().ownership(FIRST);
            first.lease(CN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
            work.fleet().saveOwnership(FIRST, first);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            Ownership second = new Ownership(DBL);
            second.lease(ELS, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
            work.fleet().saveOwnership(FIRST, second);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.fleet().ownership(FIRST).holderOn(LocalDate.of(2025, 3, 1)))
                    .isEqualTo(ELS);
            assertThat(work.fleet().ownership(FIRST).terms()).hasSize(1);
        }
    }

    @Test
    void ownershipOfAnotherCarIsRefused() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.fleet().saveOwnership(CarMark.parse("CN 400100"), new Ownership(DBL)))
                    .isInstanceOf(RegistryError.class);
        }
    }

    @Test
    void aRetirementIsWrittenAndRead() {
        Database books = stocked();
        try (UnitOfWork work = books.unitOfWork()) {
            work.fleet().retire(FIRST, LocalDate.of(2025, 3, 31), "wreck at wallace");
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.fleet().retiredOn(FIRST)).contains(LocalDate.of(2025, 3, 31));
        }
    }

    @Test
    void retiringTwiceIsRefused() {
        Database books = stocked();
        try (UnitOfWork work = books.unitOfWork()) {
            work.fleet().retire(FIRST, LocalDate.of(2025, 3, 31));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.fleet().retire(FIRST, LocalDate.of(2025, 6, 30)))
                    .isInstanceOf(RegistryError.class);
        }
    }

    @Test
    void retiringBeforeTheBuildIsRefused() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.fleet().retire(FIRST, LocalDate.of(1990, 1, 1)))
                    .isInstanceOf(RegistryError.class);
        }
    }

    @Test
    void theCarsComeBackInMarkOrder() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.fleet().all())
                    .extracting(car -> car.mark().toString())
                    .containsExactly("CN 400100", "DBL 104207", "DBL 104208");
        }
    }

    @Test
    void carsComeBackByOwner() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.fleet().byOwner(DBL)).hasSize(2);
        }
    }

    @Test
    void carsComeBackByKind() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.fleet().byKind(CarKind.COVERED_HOPPER)).hasSize(3);
            assertThat(work.fleet().byKind(CarKind.TANK)).isEmpty();
        }
    }

    @Test
    void theRepositoryBuildsTheInMemoryRegister() {
        Database books = stocked();
        try (UnitOfWork work = books.unitOfWork()) {
            Ownership ownership = work.fleet().ownership(FIRST);
            ownership.lease(CN, LocalDate.of(2025, 1, 1));
            work.fleet().saveOwnership(FIRST, ownership);
            work.fleet().retire(CarMark.parse("DBL 104208"), LocalDate.of(2025, 3, 31));
            work.commit();
        }
        FleetRegister register;
        try (UnitOfWork work = books.unitOfWork()) {
            register = work.fleet().register();
        }
        assertThat(register.size()).isEqualTo(3);
        assertThat(register.ownership(FIRST).holderOn(LocalDate.of(2025, 3, 1))).isEqualTo(CN);
        assertThat(register.retiredOn(CarMark.parse("DBL 104208"))).contains(LocalDate.of(2025, 3, 31));
    }

    @Test
    void aUnitOfWorkThatIsNotOpenHasNoSession() {
        UnitOfWork closed = new UnitOfWork(empty());
        assertThatThrownBy(closed::session).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void openingTheSameUnitOfWorkTwiceIsRefused() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(work::open).isInstanceOf(ConfigurationError.class);
        }
    }

    @Test
    void aCommittedUnitOfWorkSaysSo() {
        try (UnitOfWork work = empty().unitOfWork()) {
            work.fleet().add(aCar());
            work.commit();
            assertThat(work.committed()).isTrue();
        }
    }

    @Test
    void aRollbackUndoesWhatHasNotBeenCommitted() {
        Database books = empty();
        try (UnitOfWork work = books.unitOfWork()) {
            work.fleet().add(aCar());
            work.rollback();
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.fleet().size()).isZero();
        }
    }
}
