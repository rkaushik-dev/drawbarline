package io.github.rkaushikdev.drawbarline.carhire;

import static io.github.rkaushikdev.drawbarline.carhire.Deprescription.earnsCarHire;
import static io.github.rkaushikdev.drawbarline.carhire.Deprescription.payable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DeprescriptionTest {

    static final CarTypeCode HOPPER = new CarTypeCode("L351");
    static final LocalDate DAY = LocalDate.of(2025, 1, 14);

    static Car aCar() {
        return aCar(LocalDate.of(2020, 6, 1));
    }

    static Car aCar(LocalDate built) {
        return new Car(
                CarMark.parse("CN 400100"),
                new MechanicalDesignation("LO"),
                HOPPER,
                built,
                new Weight(63500),
                new Weight(286000),
                Length.of(59, 6));
    }

    static RateTable aSchedule() {
        RateTable table = new RateTable();
        table.add(new Band(HOPPER, 0, 9, LocalDate.of(2024, 7, 1), new Rate(4630, 1210)));
        table.add(new Band(HOPPER, 10, 99, LocalDate.of(2024, 7, 1), new Rate(3180, 1210)));
        return table;
    }

    /** The fields of an election, defaulted, for a test to change the one it is about. */
    static final class Fields {
        CarTypeCode typeCode = HOPPER;
        int fromAge = 0;
        int toAge = 9;
        LocalDate effective = LocalDate.of(2024, 7, 1);
        long hourlyTenths = 3900;

        Election build() {
            return new Election(typeCode, fromAge, toAge, effective, hourlyTenths);
        }
    }

    static Election anElection() {
        return new Fields().build();
    }

    static Fields fields() {
        return new Fields();
    }

    @Test
    void anElectionPrintsItsBandAndRate() {
        assertThat(anElection()).hasToString("L351 0-9 at 390.0c from 2024-07-01");
    }

    @Test
    void aBandThatRunsBackwardsIsRefused() {
        Fields changed = fields();
        changed.fromAge = 9;
        changed.toAge = 0;
        assertThatThrownBy(changed::build).isInstanceOf(RateError.class);
    }

    @Test
    void aBandStartingBelowZeroIsRefused() {
        Fields changed = fields();
        changed.fromAge = -1;
        assertThatThrownBy(changed::build).isInstanceOf(RateError.class);
    }

    @Test
    void aNegativeElectedRateIsRefused() {
        Fields changed = fields();
        changed.hourlyTenths = -1;
        assertThatThrownBy(changed::build).isInstanceOf(RateError.class);
    }

    @Test
    void aFilingBelowTheScheduleIsTaken() {
        Elections elections = new Elections();
        elections.file(anElection(), aSchedule());
        assertThat(elections.size()).isEqualTo(1);
    }

    @Test
    void aFilingAboveTheScheduleIsRefused() {
        Elections elections = new Elections();
        Fields changed = fields();
        changed.hourlyTenths = 5000;
        RateError caught = catchThrowableOfType(RateError.class, () -> elections.file(changed.build(), aSchedule()));
        assertThat(caught.message()).contains("never more");
    }

    @Test
    void aFilingEqualToTheScheduleIsTaken() {
        Elections elections = new Elections();
        Fields changed = fields();
        changed.hourlyTenths = 4630;
        elections.file(changed.build(), aSchedule());
        assertThat(elections.size()).isEqualTo(1);
    }

    @Test
    void aFilingForATypeWithNoScheduleIsRefused() {
        Elections elections = new Elections();
        Fields changed = fields();
        changed.typeCode = new CarTypeCode("T104");
        assertThatThrownBy(() -> elections.file(changed.build(), aSchedule())).isInstanceOf(RateError.class);
    }

    @Test
    void anElectedRateReadsBack() {
        Elections elections = new Elections();
        elections.file(anElection(), aSchedule());
        assertThat(elections.elected(HOPPER, 4, DAY)).contains(3900L);
    }

    @Test
    void aBandNobodyElectedReadsBackAsNothing() {
        Elections elections = new Elections();
        elections.file(anElection(), aSchedule());
        assertThat(elections.elected(HOPPER, 20, DAY)).isEmpty();
    }

    @Test
    void anElectionBeforeItsDateDoesNotApply() {
        Elections elections = new Elections();
        Fields changed = fields();
        changed.effective = LocalDate.of(2025, 7, 1);
        elections.file(changed.build(), aSchedule());
        assertThat(elections.elected(HOPPER, 4, DAY)).isEmpty();
    }

    @Test
    void aLaterFilingTakesOver() {
        Elections elections = new Elections();
        elections.file(anElection(), aSchedule());
        Fields changed = fields();
        changed.effective = LocalDate.of(2025, 1, 1);
        changed.hourlyTenths = 3500;
        elections.file(changed.build(), aSchedule());
        assertThat(elections.elected(HOPPER, 4, DAY)).contains(3500L);
    }

    @Test
    void theFilingsComeBackInOrder() {
        Elections elections = new Elections();
        Fields later = fields();
        later.effective = LocalDate.of(2025, 1, 1);
        elections.file(later.build(), aSchedule());
        elections.file(anElection(), aSchedule());
        assertThat(elections.filings())
                .extracting(Election::effective)
                .containsExactly(LocalDate.of(2024, 7, 1), LocalDate.of(2025, 1, 1));
    }

    @Test
    void aCarWithNoElectionEarnsTheSchedule() {
        assertThat(payable(aCar(), aSchedule(), new Elections(), DAY)).contains(new Rate(4630, 1210));
    }

    @Test
    void aCarWithAnElectionEarnsTheElectedRate() {
        Elections elections = new Elections();
        elections.file(anElection(), aSchedule());
        assertThat(payable(aCar(), aSchedule(), elections, DAY)).contains(new Rate(3900, 1210));
    }

    @Test
    void anElectionLeavesTheMileageHalfAlone() {
        Elections elections = new Elections();
        elections.file(anElection(), aSchedule());
        Rate found = payable(aCar(), aSchedule(), elections, DAY).orElseThrow();
        assertThat(found.mileageMills()).isEqualTo(1210);
    }

    @Test
    void aCarPastItsFiftyYearsEarnsNothing() {
        Car old = aCar(LocalDate.of(1970, 1, 1));
        assertThat(payable(old, aSchedule(), new Elections(), DAY)).isEmpty();
    }

    @Test
    void aCarPastItsYearsEarnsNothingEvenWithAnElection() {
        Elections elections = new Elections();
        elections.file(anElection(), aSchedule());
        Car old = aCar(LocalDate.of(1970, 1, 1));
        assertThat(payable(old, aSchedule(), elections, DAY)).isEmpty();
    }

    @Test
    void aCarBeforeItWasBuiltEarnsNothing() {
        assertThat(payable(aCar(), aSchedule(), new Elections(), LocalDate.of(1990, 1, 1)))
                .isEmpty();
    }

    @Test
    void theEligibilityCheckAgreesWithTheRate() {
        Car old = aCar(LocalDate.of(1970, 1, 1));
        assertThat(earnsCarHire(old, DAY)).isFalse();
        assertThat(earnsCarHire(aCar(), DAY)).isTrue();
    }

    @Test
    void anOlderCarFallsIntoTheLowerBand() {
        Car older = aCar(LocalDate.of(2010, 1, 1));
        assertThat(payable(older, aSchedule(), new Elections(), DAY)).contains(new Rate(3180, 1210));
    }

    @Test
    void anElectionOnOneBandLeavesTheOtherAlone() {
        Elections elections = new Elections();
        elections.file(anElection(), aSchedule());
        Car older = aCar(LocalDate.of(2010, 1, 1));
        assertThat(payable(older, aSchedule(), elections, DAY)).contains(new Rate(3180, 1210));
    }
}
