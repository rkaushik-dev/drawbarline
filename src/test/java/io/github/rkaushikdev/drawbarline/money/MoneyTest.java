package io.github.rkaushikdev.drawbarline.money;

import static io.github.rkaushikdev.drawbarline.money.Money.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void centsGoInAndComeBack() {
        assertThat(new Money(1234).cents()).isEqualTo(1234);
    }

    @Test
    void dollarsAndCentsBuildAnAmount() {
        assertThat(Money.dollars(12, 34)).isEqualTo(new Money(1234));
    }

    @Test
    void anAmountMayBeNegative() {
        assertThat(new Money(-500).cents()).isEqualTo(-500);
    }

    @Test
    void aFloatIsNotAnAmount() {
        // An amount is a whole number of cents: the only way in is a long, and the compiler
        // refuses a fraction.
        assertThat(Money.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(long.class);
        for (Method method : Money.class.getMethods()) {
            if (method.getName().equals("dollars")) {
                assertThat(method.getParameterTypes()).containsOnly(long.class);
            }
        }
    }

    @Test
    void aBooleanIsNotAnAmount() {
        // A flag is not a count of cents and no constructor takes one.
        for (Constructor<?> constructor : Money.class.getConstructors()) {
            assertThat(constructor.getParameterTypes()).doesNotContain(boolean.class, Boolean.class);
        }
    }

    @ParameterizedTest
    @CsvSource({"'$1,234.56', 123456", "1234.56, 123456", "$4, 400", "-$4.00, -400", "'-1,000.05', -100005"})
    void parseReadsTheFormsAnInvoiceUses(String text, long cents) {
        assertThat(Money.parse(text)).isEqualTo(new Money(cents));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "free", "$1.5", "$1.234", "1.2.3"})
    void parseRefusesWhatItCannotRead(String bad) {
        assertThatThrownBy(() -> Money.parse(bad)).isInstanceOf(MoneyError.class);
    }

    @Test
    void anAmountPrintsGroupedWithADollarSign() {
        assertThat(new Money(123456)).hasToString("$1,234.56");
    }

    @Test
    void aNegativeAmountPrintsItsSignBeforeTheDollar() {
        assertThat(new Money(-123456)).hasToString("-$1,234.56");
    }

    @Test
    void aBareCentAmountPrintsWithALeadingZero() {
        assertThat(new Money(5)).hasToString("$0.05");
    }

    @Test
    void zeroKnowsItIsZero() {
        assertThat(ZERO.isZero()).isTrue();
        assertThat(new Money(1).isZero()).isFalse();
    }

    @Test
    void amountsAddAndSubtract() {
        assertThat(new Money(500).plus(new Money(250))).isEqualTo(new Money(750));
        assertThat(new Money(500).minus(new Money(750))).isEqualTo(new Money(-250));
    }

    @Test
    void anAmountNegatesAndTakesAnAbsolute() {
        assertThat(new Money(500).negate()).isEqualTo(new Money(-500));
        assertThat(new Money(-500).abs()).isEqualTo(new Money(500));
    }

    @Test
    void anAmountScalesByACount() {
        assertThat(new Money(125).times(8)).isEqualTo(new Money(1000));
        assertThat(Money.total(Collections.nCopies(8, new Money(125)))).isEqualTo(new Money(125).times(8));
    }

    @Test
    void anAmountScalesByANegativeCount() {
        assertThat(new Money(125).times(-2)).isEqualTo(new Money(-250));
    }

    @Test
    void amountsOrderAndSort() {
        assertThat(new Money(-100)).isLessThan(ZERO);
        assertThat(ZERO).isLessThan(new Money(100));
        assertThat(List.of(new Money(100), new Money(-100)).stream().sorted().toList())
                .containsExactly(new Money(-100), new Money(100));
    }

    @Test
    void aTotalOverNothingIsZero() {
        assertThat(Money.total(List.of())).isEqualTo(ZERO);
    }

    @Test
    void aTotalAddsSignedAmounts() {
        assertThat(Money.total(List.of(new Money(500), new Money(-200), new Money(50))))
                .isEqualTo(new Money(350));
    }

    @Test
    void anEvenAllocationDividesExactly() {
        assertThat(Money.allocate(new Money(1000), 1, 1)).containsExactly(new Money(500), new Money(500));
    }

    @Test
    void anAllocationNeverLosesACent() {
        List<Money> shares = Money.allocate(new Money(1000), 1, 1, 1);
        assertThat(Money.total(shares)).isEqualTo(new Money(1000));
        assertThat(shares).containsExactly(new Money(334), new Money(333), new Money(333));
    }

    @Test
    void anAllocationFollowsTheWeights() {
        assertThat(Money.allocate(new Money(1000), 3, 1)).containsExactly(new Money(750), new Money(250));
    }

    @Test
    void oddCentsGoToTheLargestRemainders() {
        List<Money> shares = Money.allocate(new Money(100), 1, 1, 1, 1, 1, 1);
        assertThat(Money.total(shares)).isEqualTo(new Money(100));
        assertThat(shares).extracting(Money::cents).containsExactly(17L, 17L, 17L, 17L, 16L, 16L);
    }

    @Test
    void aNegativeAmountAllocatesWithItsSign() {
        List<Money> shares = Money.allocate(new Money(-1000), 1, 1, 1);
        assertThat(Money.total(shares)).isEqualTo(new Money(-1000));
        assertThat(shares).containsExactly(new Money(-334), new Money(-333), new Money(-333));
    }

    @Test
    void aZeroWeightTakesNothing() {
        assertThat(Money.allocate(new Money(1000), 1, 0)).containsExactly(new Money(1000), ZERO);
    }

    @Test
    void allocatingAcrossNothingIsRefused() {
        assertThatThrownBy(() -> Money.allocate(new Money(100), List.of())).isInstanceOf(MoneyError.class);
    }

    @Test
    void allocatingAcrossAllZeroWeightsIsRefused() {
        assertThatThrownBy(() -> Money.allocate(new Money(100), 0, 0)).isInstanceOf(MoneyError.class);
    }

    @Test
    void aNegativeWeightIsRefused() {
        assertThatThrownBy(() -> Money.allocate(new Money(100), 1, -1)).isInstanceOf(MoneyError.class);
    }

    @Test
    void aSplitHandsOutTheWholeAmount() {
        List<Money> shares = Money.split(new Money(1000), 7);
        assertThat(Money.total(shares)).isEqualTo(new Money(1000));
        assertThat(shares).hasSize(7);
    }

    @Test
    void aSplitFewerThanOneWayIsRefused() {
        assertThatThrownBy(() -> Money.split(new Money(100), 0)).isInstanceOf(MoneyError.class);
    }
}
