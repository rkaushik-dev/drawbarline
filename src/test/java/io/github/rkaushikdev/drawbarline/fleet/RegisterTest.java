package io.github.rkaushikdev.drawbarline.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.DuplicateCar;
import io.github.rkaushikdev.drawbarline.RegistryError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegisterTest {

    record Kinds(MechanicalDesignation designation, CarTypeCode typeCode) {}

    static final Kinds HOPPER = new Kinds(new MechanicalDesignation("LO"), new CarTypeCode("L351"));
    static final Kinds BOX = new Kinds(new MechanicalDesignation("XM"), new CarTypeCode("X202"));

    static Car aCar(String mark) {
        return aCar(mark, HOPPER, LocalDate.of(1998, 6, 1));
    }

    static Car aCar(String mark, Kinds kinds) {
        return aCar(mark, kinds, LocalDate.of(1998, 6, 1));
    }

    static Car aCar(String mark, Kinds kinds, LocalDate built) {
        return new Car(
                CarMark.parse(mark),
                kinds.designation(),
                kinds.typeCode(),
                built,
                new Weight(63500),
                new Weight(286000),
                Length.of(59, 6));
    }

    static FleetRegister aRegister() {
        FleetRegister register = new FleetRegister();
        register.add(aCar("DBL 104207"));
        register.add(aCar("DBL 104208"));
        register.add(aCar("CN 400100", BOX));
        register.add(aCar("PLMX 9001", BOX));
        return register;
    }

    static List<String> marks(Iterable<Car> cars) {
        List<String> found = new ArrayList<>();
        cars.forEach(car -> found.add(car.mark().toString()));
        return found;
    }

    @Test
    void anEmptyRegisterHoldsNothing() {
        assertThat(new FleetRegister().size()).isZero();
    }

    @Test
    void aCarGoesOnAndComesBack() {
        FleetRegister register = new FleetRegister();
        Car car = register.add(aCar("DBL 104207"));
        assertThat(register.get(CarMark.parse("DBL 104207"))).isSameAs(car);
    }

    @Test
    void theSameCarTwiceIsRefused() {
        FleetRegister register = aRegister();
        assertThatThrownBy(() -> register.add(aCar("DBL 104207"))).isInstanceOf(DuplicateCar.class);
    }

    @Test
    void aCarNobodyFiledIsUnknown() {
        assertThatThrownBy(() -> aRegister().get(CarMark.parse("WC 1"))).isInstanceOf(UnknownCar.class);
    }

    @Test
    void membershipReadsOffTheMark() {
        FleetRegister register = aRegister();
        assertThat(register.contains(CarMark.parse("DBL 104207"))).isTrue();
        assertThat(register.contains(CarMark.parse("WC 1"))).isFalse();
        assertThat(register.contains("DBL 104207")).isFalse();
    }

    @Test
    void theRegisterIteratesInMarkOrder() {
        assertThat(marks(aRegister())).containsExactly("CN 400100", "DBL 104207", "DBL 104208", "PLMX 009001");
    }

    @Test
    void aCorrectedRecordReplacesTheOldOne() {
        FleetRegister register = aRegister();
        Car corrected = aCar("DBL 104207", HOPPER, LocalDate.of(1999, 1, 1));
        register.replace(corrected);
        assertThat(register.get(CarMark.parse("DBL 104207")).built()).isEqualTo(LocalDate.of(1999, 1, 1));
    }

    @Test
    void aCorrectionKeepsTheLeaseHistory() {
        FleetRegister register = aRegister();
        CarMark mark = CarMark.parse("DBL 104207");
        register.ownership(mark).lease(new ReportingMark("CN"), LocalDate.of(2025, 1, 1));
        register.replace(aCar("DBL 104207", HOPPER, LocalDate.of(1999, 1, 1)));
        assertThat(register.ownership(mark).holderOn(LocalDate.of(2025, 3, 1))).isEqualTo(new ReportingMark("CN"));
    }

    @Test
    void correctingACarNobodyFiledIsRefused() {
        assertThatThrownBy(() -> aRegister().replace(aCar("WC 1"))).isInstanceOf(UnknownCar.class);
    }

    @Test
    void aNewCarStartsOwedToItsOwner() {
        FleetRegister register = aRegister();
        CarMark mark = CarMark.parse("CN 400100");
        assertThat(register.ownership(mark).holderOn(LocalDate.of(2025, 3, 1))).isEqualTo(new ReportingMark("CN"));
    }

    @Test
    void ownershipOfACarNobodyFiledIsRefused() {
        assertThatThrownBy(() -> aRegister().ownership(CarMark.parse("WC 1"))).isInstanceOf(UnknownCar.class);
    }

    @Test
    void aCarIsInServiceTheDayItWasBuilt() {
        assertThat(aRegister().inServiceOn(CarMark.parse("DBL 104207"), LocalDate.of(1998, 6, 1)))
                .isTrue();
    }

    @Test
    void aCarIsNotInServiceBeforeItWasBuilt() {
        assertThat(aRegister().inServiceOn(CarMark.parse("DBL 104207"), LocalDate.of(1998, 5, 31)))
                .isFalse();
    }

    @Test
    void aRetiredCarStaysInServiceThroughItsLastDay() {
        FleetRegister register = aRegister();
        CarMark mark = CarMark.parse("DBL 104207");
        register.retire(mark, LocalDate.of(2025, 3, 31));
        assertThat(register.inServiceOn(mark, LocalDate.of(2025, 3, 31))).isTrue();
        assertThat(register.inServiceOn(mark, LocalDate.of(2025, 4, 1))).isFalse();
    }

    @Test
    void aRetiredCarIsStillOnTheRegister() {
        FleetRegister register = aRegister();
        CarMark mark = CarMark.parse("DBL 104207");
        register.retire(mark, LocalDate.of(2025, 3, 31));
        assertThat(register.get(mark)).isNotNull();
        assertThat(register.retiredOn(mark)).contains(LocalDate.of(2025, 3, 31));
    }

    @Test
    void aCarStillRunningHasNoRetirementDate() {
        assertThat(aRegister().retiredOn(CarMark.parse("DBL 104207"))).isEmpty();
    }

    @Test
    void retiringACarTwiceIsRefused() {
        FleetRegister register = aRegister();
        CarMark mark = CarMark.parse("DBL 104207");
        register.retire(mark, LocalDate.of(2025, 3, 31));
        assertThatThrownBy(() -> register.retire(mark, LocalDate.of(2025, 6, 30)))
                .isInstanceOf(RegistryError.class);
    }

    @Test
    void retiringACarBeforeItWasBuiltIsRefused() {
        FleetRegister register = aRegister();
        assertThatThrownBy(() -> register.retire(CarMark.parse("DBL 104207"), LocalDate.of(1990, 1, 1)))
                .isInstanceOf(RegistryError.class);
    }

    @Test
    void aSnapshotLeavesOutWhatIsRetired() {
        FleetRegister register = aRegister();
        register.retire(CarMark.parse("DBL 104207"), LocalDate.of(2025, 3, 31));
        List<String> marks = marks(register.snapshot(LocalDate.of(2025, 6, 1)));
        assertThat(marks).doesNotContain("DBL 104207");
        assertThat(marks).hasSize(3);
    }

    @Test
    void carsComeBackByKind() {
        assertThat(aRegister().byKind(CarKind.BOX)).hasSize(2);
        assertThat(aRegister().byKind(CarKind.TANK)).isEmpty();
    }

    @Test
    void carsComeBackByTypeCode() {
        assertThat(aRegister().byTypeCode(new CarTypeCode("L351"))).hasSize(2);
    }

    @Test
    void carsComeBackByOwner() {
        assertThat(marks(aRegister().byOwner(new ReportingMark("DBL")))).containsExactly("DBL 104207", "DBL 104208");
    }

    @Test
    void privateCarsComeBackOnTheirOwn() {
        assertThat(marks(aRegister().privateCars())).containsExactly("PLMX 009001");
    }

    @Test
    void carsRunningOutOfLifeComeBackTogether() {
        FleetRegister register = aRegister();
        register.add(aCar("CN 300001", BOX, LocalDate.of(1972, 1, 1)));
        assertThat(marks(register.expiringBefore(LocalDate.of(2025, 1, 1)))).containsExactly("CN 300001");
    }

    @Test
    void carsComeBackByWhoHoldsThem() {
        FleetRegister register = aRegister();
        register.ownership(CarMark.parse("DBL 104207"))
                .lease(new ReportingMark("ELS"), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        List<Car> held = register.heldBy(new ReportingMark("ELS"), LocalDate.of(2025, 3, 1));
        assertThat(marks(held)).containsExactly("DBL 104207");
    }
}
