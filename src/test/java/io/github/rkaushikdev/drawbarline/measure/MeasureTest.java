package io.github.rkaushikdev.drawbarline.measure;

import static io.github.rkaushikdev.drawbarline.measure.Length.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeasureTest {

    @Test
    void feetAndInchesGoInAndComeBack() {
        Length car = Length.of(89, 4);
        assertThat(car.inches()).isEqualTo(1072);
        assertThat(car.feet()).isEqualTo(89);
        assertThat(car.inchesPart()).isEqualTo(4);
    }

    @Test
    void inchesOverElevenRollIntoFeet() {
        assertThat(Length.of(0, 25)).isEqualTo(Length.of(2, 1));
    }

    @Test
    void aWholeNumberOfFeetPrintsWithoutInches() {
        assertThat(Length.of(50)).hasToString("50'");
    }

    @Test
    void feetAndInchesPrintTheWayAStencilReads() {
        assertThat(Length.of(89, 4)).hasToString("89'4\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"89'4\"", "89' 4\"", "89ft 4in", "89 FT 4 IN", "89-4"})
    void parseReadsTheFormsTheRegisterUses(String text) {
        assertThat(Length.parse(text)).isEqualTo(Length.of(89, 4));
    }

    @Test
    void parseReadsFeetWithNoInches() {
        assertThat(Length.parse("50'")).isEqualTo(Length.of(50));
    }

    @Test
    void parseReadsBareInches() {
        assertThat(Length.parse("9\"")).isEqualTo(new Length(9));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "long", "89", "-4'", "89'99\""})
    void parseRefusesWhatItCannotRead(String bad) {
        assertThatThrownBy(() -> Length.parse(bad)).isInstanceOf(MeasureError.class);
    }

    @Test
    void aNegativeLengthIsRefused() {
        assertThatThrownBy(() -> new Length(-1)).isInstanceOf(MeasureError.class);
    }

    @Test
    void aFloatIsNotALength() {
        // A length is whole inches; the compiler refuses a fraction.
        assertThat(Length.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(int.class);
    }

    @Test
    void aBooleanIsNotALength() {
        // Feet and inches are whole numbers on the way in as well; no builder takes a flag.
        for (Method method : Length.class.getMethods()) {
            if (method.getName().equals("of")) {
                assertThat(method.getParameterTypes()).containsOnly(int.class);
            }
        }
    }

    @Test
    void lengthsAdd() {
        assertThat(Length.of(4).plus(Length.of(3, 6))).isEqualTo(Length.of(7, 6));
    }

    @Test
    void lengthsSubtract() {
        assertThat(Length.of(89, 4).minus(Length.of(50))).isEqualTo(Length.of(39, 4));
    }

    @Test
    void aSubtractionThatRunsPastZeroIsRefused() {
        MeasureError caught =
                catchThrowableOfType(MeasureError.class, () -> Length.of(3).minus(Length.of(4)));
        assertThat(caught.context().get("left")).isEqualTo("3'");
    }

    @Test
    void aLengthScalesByACount() {
        assertThat(Length.of(50).times(3)).isEqualTo(Length.of(150));
        assertThat(Length.total(List.of(Length.of(50), Length.of(50), Length.of(50))))
                .isEqualTo(Length.of(50).times(3));
    }

    @Test
    void aLengthDoesNotScaleByANegative() {
        assertThatThrownBy(() -> Length.of(50).times(-1)).isInstanceOf(MeasureError.class);
    }

    @Test
    void aLengthDoesNotScaleByAFloat() {
        // Scaling takes a whole count; the compiler refuses one and a half.
        Method[] scalers = Arrays.stream(Length.class.getMethods())
                .filter(method -> method.getName().equals("times"))
                .toArray(Method[]::new);
        assertThat(scalers).hasSize(1);
        assertThat(scalers[0].getParameterTypes()).containsExactly(int.class);
    }

    @Test
    void lengthsOrderAndSort() {
        assertThat(Length.of(40)).isLessThan(Length.of(50));
        assertThat(List.of(Length.of(50), Length.of(40)).stream().sorted().toList())
                .containsExactly(Length.of(40), Length.of(50));
    }

    @Test
    void decimalFeetIsAvailableForAReport() {
        assertThat(Length.of(89, 6).inFeet()).isEqualTo(89.5);
    }

    @Test
    void theLongestOfSeveral() {
        assertThat(Length.longest(List.of(Length.of(40), Length.of(89, 4), Length.of(60))))
                .isEqualTo(Length.of(89, 4));
    }

    @Test
    void theLongestOfNoneIsRefused() {
        assertThatThrownBy(() -> Length.longest(List.of())).isInstanceOf(MeasureError.class);
    }

    @Test
    void aTotalOverNothingIsZero() {
        assertThat(Length.total(List.of())).isEqualTo(ZERO);
    }

    @Test
    void aTotalAddsThemAll() {
        assertThat(Length.total(List.of(Length.of(50), Length.of(40, 6), Length.of(0, 6))))
                .isEqualTo(Length.of(91));
    }
}
