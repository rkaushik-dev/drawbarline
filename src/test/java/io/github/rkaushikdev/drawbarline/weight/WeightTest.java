package io.github.rkaushikdev.drawbarline.weight;

import static io.github.rkaushikdev.drawbarline.weight.Weight.NOTHING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeightTest {

    @Test
    void poundsGoInAndComeBack() {
        assertThat(new Weight(63500).pounds()).isEqualTo(63500);
    }

    @Test
    void tonsAreShortTons() {
        assertThat(Weight.tons(100)).isEqualTo(new Weight(200000));
    }

    @Test
    void fractionalTonsAreRefusedRatherThanRounded() throws NoSuchMethodException {
        // Tons are whole here; the compiler refuses a hundred and a half.
        assertThat(Weight.class.getMethod("tons", long.class).getParameterTypes())
                .containsExactly(long.class);
    }

    @Test
    void aNegativeWeightIsRefused() {
        assertThatThrownBy(() -> new Weight(-1)).isInstanceOf(WeightError.class);
    }

    @Test
    void aFloatIsNotAWeight() {
        // A weight is whole pounds; the compiler refuses a fraction.
        assertThat(Weight.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(long.class);
    }

    @Test
    void aBooleanIsNotAWeight() {
        for (Constructor<?> constructor : Weight.class.getConstructors()) {
            assertThat(constructor.getParameterTypes()).doesNotContain(boolean.class, Boolean.class);
        }
    }

    @Test
    void aWeightPrintsGroupedAndInPounds() {
        assertThat(new Weight(263000)).hasToString("263,000 lb");
    }

    @Test
    void decimalTonsAreAvailableForAReport() {
        assertThat(new Weight(263000).inTons()).isEqualTo(131.5);
    }

    @Test
    void weightsAddAndSubtract() {
        assertThat(new Weight(1000).plus(new Weight(500))).isEqualTo(new Weight(1500));
        assertThat(new Weight(1000).minus(new Weight(500))).isEqualTo(new Weight(500));
    }

    @Test
    void aSubtractionPastZeroIsRefused() {
        WeightError caught = catchThrowableOfType(WeightError.class, () -> new Weight(500).minus(new Weight(1000)));
        assertThat(caught.context().get("right")).isEqualTo("1,000 lb");
    }

    @Test
    void aWeightScalesByACount() {
        assertThat(new Weight(1000).times(4)).isEqualTo(new Weight(4000));
        Weight four = NOTHING;
        for (Weight one : Collections.nCopies(4, new Weight(1000))) {
            four = four.plus(one);
        }
        assertThat(four).isEqualTo(new Weight(1000).times(4));
    }

    @Test
    void aWeightDoesNotScaleByANegative() {
        assertThatThrownBy(() -> new Weight(1000).times(-2)).isInstanceOf(WeightError.class);
    }

    @Test
    void weightsOrderAndSort() {
        assertThat(new Weight(100)).isLessThan(new Weight(200));
        assertThat(Collections.max(List.of(new Weight(100), new Weight(300), new Weight(200))))
                .isEqualTo(new Weight(300));
    }

    @ParameterizedTest
    @CsvSource({"SEVENTY, 220000", "HUNDRED, 263000", "ONE_TEN, 286000", "ONE_TWENTY_FIVE, 315000"})
    void aCapacityClassNamesARealGrossRailLoad(NominalCapacity capacity, long pounds) {
        assertThat(capacity.grossRailLoad()).isEqualTo(new Weight(pounds));
    }

    @Test
    void aHundredTonCarDoesNotWeighAHundredTons() {
        assertThat(NominalCapacity.HUNDRED.grossRailLoad()).isNotEqualTo(Weight.tons(100));
    }

    @Test
    void aCapacityClassLabelsItself() {
        assertThat(NominalCapacity.ONE_TWENTY_FIVE.label()).isEqualTo("125 ton");
    }

    @Test
    void theClassReadsBackOffAGrossRailLoad() {
        assertThat(Weight.capacityFor(new Weight(286000))).isSameAs(NominalCapacity.ONE_TEN);
    }

    @Test
    void anUnknownGrossRailLoadHasNoClass() {
        assertThatThrownBy(() -> Weight.capacityFor(new Weight(250000))).isInstanceOf(WeightError.class);
    }

    @Test
    void theLoadLimitIsTheGrossRailLoadLessTheLightWeight() {
        assertThat(Weight.loadLimit(new Weight(286000), new Weight(63500))).isEqualTo(new Weight(222500));
    }

    @Test
    void aCarThatWeighsItsOwnGrossRailLoadIsRefused() {
        assertThatThrownBy(() -> Weight.loadLimit(new Weight(220000), new Weight(220000)))
                .isInstanceOf(WeightError.class);
    }

    @Test
    void aLadingInsideTheLimitIsNoOverload() {
        assertThat(Weight.overload(new Weight(200000), new Weight(222500))).isEqualTo(NOTHING);
    }

    @Test
    void aLadingExactlyOnTheLimitIsNoOverload() {
        assertThat(Weight.overload(new Weight(222500), new Weight(222500))).isEqualTo(NOTHING);
    }

    @Test
    void anOverloadReportsHowFarPast() {
        assertThat(Weight.overload(new Weight(225000), new Weight(222500))).isEqualTo(new Weight(2500));
    }
}
