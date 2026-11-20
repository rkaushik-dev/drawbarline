package io.github.rkaushikdev.drawbarline.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.NominalCapacity;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import io.github.rkaushikdev.drawbarline.weight.WeightError;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CarTest {

    /** The fields of the car every test starts from; a test changes the one it is about. */
    static final class Fields {
        CarMark mark = CarMark.parse("DBL 104207");
        MechanicalDesignation designation = new MechanicalDesignation("LO");
        CarTypeCode typeCode = new CarTypeCode("L351");
        LocalDate built = LocalDate.of(1998, 6, 1);
        Weight lightWeight = new Weight(63500);
        Weight grossRailLoad = new Weight(286000);
        Length outsideLength = Length.of(59, 6);
        Optional<LocalDate> rebuilt = Optional.empty();

        Car build() {
            return new Car(mark, designation, typeCode, built, lightWeight, grossRailLoad, outsideLength, rebuilt);
        }
    }

    static Car aCar() {
        return new Fields().build();
    }

    static Fields fields() {
        return new Fields();
    }

    @Test
    void aCarPrintsItsMarksAndDesignation() {
        assertThat(aCar()).hasToString("DBL 104207 LO");
    }

    @Test
    void aCarKnowsItsKind() {
        assertThat(aCar().kind()).isSameAs(CarKind.COVERED_HOPPER);
    }

    @Test
    void aCarKnowsItsOwner() {
        assertThat(aCar().owner()).hasToString("DBL");
    }

    @Test
    void aRailroadCarIsNotPrivate() {
        assertThat(aCar().isPrivate()).isFalse();
    }

    @Test
    void aCarUnderAPrivateMarkIsPrivate() {
        Fields changed = fields();
        changed.mark = CarMark.parse("PLMX 4207");
        assertThat(changed.build().isPrivate()).isTrue();
    }

    @Test
    void theLoadLimitComesOffTheTwoWeights() {
        assertThat(aCar().loadLimit()).isEqualTo(new Weight(222500));
    }

    @Test
    void theCapacityClassComesOffTheGrossRailLoad() {
        assertThat(aCar().capacity()).isSameAs(NominalCapacity.ONE_TEN);
    }

    @Test
    void aDesignationAndTypeCodeThatDisagreeAreRefused() {
        Fields changed = fields();
        changed.typeCode = new CarTypeCode("X402");
        CarRecordError caught = catchThrowableOfType(CarRecordError.class, changed::build);
        assertThat(caught.context().get("car")).isEqualTo("DBL 104207");
    }

    @Test
    void aCarThatWeighsItsOwnGrossRailLoadIsRefused() {
        Fields changed = fields();
        changed.lightWeight = new Weight(286000);
        assertThatThrownBy(changed::build).isInstanceOf(WeightError.class);
    }

    @Test
    void aRebuildBeforeTheBuildIsRefused() {
        Fields changed = fields();
        changed.rebuilt = Optional.of(LocalDate.of(1990, 1, 1));
        assertThatThrownBy(changed::build).isInstanceOf(CarRecordError.class);
    }

    @Test
    void aRebuildOnTheBuildDateIsAllowed() {
        Fields changed = fields();
        changed.rebuilt = Optional.of(LocalDate.of(1998, 6, 1));
        assertThat(changed.build().datesFrom()).isEqualTo(LocalDate.of(1998, 6, 1));
    }

    @Test
    void theFiftyYearClockRunsFromTheBuild() {
        assertThat(aCar().interchangeExpires()).isEqualTo(LocalDate.of(2048, 6, 1));
    }

    @Test
    void aRebuildResetsTheFiftyYearClock() {
        Fields changed = fields();
        changed.rebuilt = Optional.of(LocalDate.of(2012, 9, 15));
        assertThat(changed.build().interchangeExpires()).isEqualTo(LocalDate.of(2062, 9, 15));
    }

    @Test
    void aLeapDayBuildExpiresOnTheEndOfFebruary() {
        Fields changed = fields();
        changed.built = LocalDate.of(2000, 2, 29);
        assertThat(changed.build().interchangeExpires()).isEqualTo(LocalDate.of(2050, 2, 28));
    }

    @Test
    void aCarInsideItsLifeIsEligible() {
        assertThat(aCar().eligibleOn(LocalDate.of(2025, 1, 14))).isTrue();
    }

    @Test
    void aCarPastItsLifeIsNotEligible() {
        Fields changed = fields();
        changed.built = LocalDate.of(1970, 1, 1);
        assertThat(changed.build().eligibleOn(LocalDate.of(2025, 1, 14))).isFalse();
    }

    @Test
    void aCarOnTheDayItExpiresIsNotEligible() {
        assertThat(aCar().eligibleOn(LocalDate.of(2048, 6, 1))).isFalse();
    }

    @Test
    void aCarBeforeItWasBuiltIsNotEligible() {
        assertThat(aCar().eligibleOn(LocalDate.of(1997, 1, 1))).isFalse();
    }

    @Test
    void ageCountsWholeYears() {
        assertThat(aCar().ageOn(LocalDate.of(2025, 6, 1))).isEqualTo(27);
    }

    @Test
    void ageDoesNotRoundUpAPartYear() {
        assertThat(aCar().ageOn(LocalDate.of(2025, 5, 31))).isEqualTo(26);
    }

    @Test
    void ageBeforeTheCarExistedIsRefused() {
        assertThatThrownBy(() -> aCar().ageOn(LocalDate.of(1990, 1, 1))).isInstanceOf(CarRecordError.class);
    }

    @Test
    void twoRecordsOfTheSameCarCompareEqual() {
        assertThat(aCar()).isEqualTo(aCar());
    }

    @Test
    void aCarRecordIsHashable() {
        assertThat(new HashSet<>(List.of(aCar(), aCar()))).hasSize(1);
    }
}
