package io.github.rkaushikdev.drawbarline.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AcceptanceTest {

    static final LocalDate DAY = LocalDate.of(2025, 1, 14);
    static final CarMark CAR = CarMark.parse("DBL 104207");

    FleetRegister register;

    static Car aCar(LocalDate built) {
        return new Car(
                CarMark.parse("DBL 104207"),
                new MechanicalDesignation("LO"),
                new CarTypeCode("L351"),
                built,
                new Weight(63500),
                new Weight(286000),
                Length.of(59, 6));
    }

    @BeforeEach
    void aRegister() {
        register = new FleetRegister();
        register.add(aCar(LocalDate.of(1998, 6, 1)));
    }

    static List<Finding> laden(FleetRegister register, Weight lading) {
        return Acceptance.inspect(register, CAR, DAY, Optional.of(lading), List.of());
    }

    @Test
    void aSoundCarDrawsNoFindings() {
        assertThat(Acceptance.inspect(register, CAR, DAY)).isEmpty();
    }

    @Test
    void aSoundCarIsAcceptable() {
        assertThat(Acceptance.acceptable(register, CAR, DAY)).isTrue();
    }

    @Test
    void aCarWithNoRecordIsRefused() {
        List<Finding> findings = Acceptance.inspect(register, CarMark.parse("WC 1"), DAY);
        assertThat(findings).extracting(Finding::reason).containsExactly(Refusal.NOT_REGISTERED);
    }

    @Test
    void aCarWithNoRecordDrawsNothingElse() {
        List<Finding> findings =
                Acceptance.inspect(register, CarMark.parse("WC 1"), DAY, Optional.empty(), List.of("BROKEN"));
        assertThat(findings).hasSize(1);
    }

    @Test
    void aRetiredCarIsRefused() {
        register.retire(CAR, LocalDate.of(2024, 12, 31));
        List<Finding> findings = Acceptance.inspect(register, CAR, DAY);
        assertThat(findings).extracting(Finding::reason).containsExactly(Refusal.RETIRED);
        assertThat(findings.get(0).detail()).isEqualTo("2024-12-31");
    }

    @Test
    void aCarDatedBeforeItExistedIsRefused() {
        List<Finding> findings = Acceptance.inspect(register, CAR, LocalDate.of(1990, 1, 1));
        assertThat(findings).extracting(Finding::reason).containsExactly(Refusal.NOT_YET_BUILT);
    }

    @Test
    void aCarPastFiftyYearsIsRefused() {
        FleetRegister old = new FleetRegister();
        old.add(aCar(LocalDate.of(1970, 1, 1)));
        List<Finding> findings = Acceptance.inspect(old, CAR, DAY);
        assertThat(findings).extracting(Finding::reason).containsExactly(Refusal.AGE);
    }

    @Test
    void aLadingInsideTheLoadLimitPasses() {
        assertThat(laden(register, new Weight(200000))).isEmpty();
    }

    @Test
    void aLadingOnTheLoadLimitPasses() {
        assertThat(laden(register, new Weight(222500))).isEmpty();
    }

    @Test
    void anOverloadIsRefusedAndSaysHowFar() {
        List<Finding> findings = laden(register, new Weight(225000));
        assertThat(findings).extracting(Finding::reason).containsExactly(Refusal.OVERLOADED);
        assertThat(findings.get(0).detail()).isEqualTo("over by 2,500 lb");
    }

    @Test
    void everyDefectTheInspectorFindsIsItsOwnFinding() {
        List<Finding> findings = Acceptance.inspect(register, CAR, DAY, Optional.empty(), List.of("WHEEL", "COUPLER"));
        assertThat(findings).extracting(Finding::detail).containsExactly("WHEEL", "COUPLER");
    }

    @Test
    void findingsPileUpRatherThanStoppingAtTheFirst() {
        register.retire(CAR, LocalDate.of(2024, 12, 31));
        List<Finding> findings =
                Acceptance.inspect(register, CAR, DAY, Optional.of(new Weight(300000)), List.of("WHEEL"));
        assertThat(findings)
                .extracting(Finding::reason)
                .containsExactly(Refusal.RETIRED, Refusal.OVERLOADED, Refusal.DEFECT);
    }

    @Test
    void aFindingWithNoDetailPrintsTheReasonAlone() {
        assertThat(new Finding(Refusal.AGE)).hasToString("past fifty years in interchange service");
    }

    @Test
    void aFindingWithDetailPrintsItInBrackets() {
        assertThat(new Finding(Refusal.DEFECT, "WHEEL"))
                .hasToString("carries a defect that has to be repaired first (WHEEL)");
    }

    @Test
    void aCarWithAnyFindingIsNotAcceptable() {
        assertThat(Acceptance.acceptable(register, CAR, DAY, Optional.empty(), List.of("WHEEL")))
                .isFalse();
    }

    @Test
    void everyRefusalCarriesReadableText() {
        assertThat(Refusal.values())
                .allSatisfy(reason -> assertThat(reason.text()).isNotEmpty());
    }
}
