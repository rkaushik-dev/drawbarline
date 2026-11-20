package io.github.rkaushikdev.drawbarline.demurrage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TariffTest {

    static final List<Step> STEPS = List.of(new Step(2, 10000), new Step(4, 15000), new Step(0, 20000));

    static Tariff tariff(String code, LocalDate effective, int freeDaysLoaded, int freeDaysEmpty, List<Step> steps) {
        return new Tariff(code, effective, freeDaysLoaded, freeDaysEmpty, steps);
    }

    static Tariff aTariff() {
        return tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, STEPS);
    }

    static Tariff aTariff(List<Step> steps) {
        return tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, steps);
    }

    static Tariff aTariff(String code, LocalDate effective) {
        return tariff(code, effective, 2, 1, STEPS);
    }

    @Test
    void aTariffPrintsItsNumberAndDate() {
        assertThat(aTariff()).hasToString("tariff 4200 from 2024-07-01");
    }

    @Test
    void aClosedStepPrintsTheDayItRunsThrough() {
        assertThat(new Step(2, 10000)).hasToString("$100.00 through day 2");
    }

    @Test
    void theLastStepPrintsAsRunningOn() {
        assertThat(new Step(0, 20000)).hasToString("$200.00 and thereafter");
    }

    @Test
    void aStepThatPaysTheCustomerIsRefused() {
        assertThatThrownBy(() -> new Step(2, -1)).isInstanceOf(TariffError.class);
    }

    @Test
    void aStepThroughANegativeDayIsRefused() {
        assertThatThrownBy(() -> new Step(-1, 10000)).isInstanceOf(TariffError.class);
    }

    @Test
    void freeTimeDiffersBetweenLoadsAndEmpties() {
        Tariff tariff = aTariff();
        assertThat(tariff.freeDays(true)).isEqualTo(2);
        assertThat(tariff.freeDays(false)).isEqualTo(1);
    }

    @Test
    void negativeFreeTimeIsRefused() {
        assertThatThrownBy(() -> tariff("4200", LocalDate.of(2024, 7, 1), -1, 1, STEPS))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void aTariffWithNoStepsIsRefused() {
        assertThatThrownBy(() -> aTariff(List.of())).isInstanceOf(TariffError.class);
    }

    @Test
    void aTariffWhoseLastStepEndsIsRefused() {
        assertThatThrownBy(() -> aTariff(List.of(new Step(2, 10000), new Step(4, 15000))))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void anOpenStepBeforeTheEndIsRefused() {
        assertThatThrownBy(() -> aTariff(List.of(new Step(0, 10000), new Step(4, 15000), new Step(0, 20000))))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void stepsThatDoNotClimbAreRefused() {
        assertThatThrownBy(() -> aTariff(List.of(new Step(4, 10000), new Step(2, 15000), new Step(0, 20000))))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void aTariffWithNoNumberIsRefused() {
        assertThatThrownBy(() -> aTariff("  ", LocalDate.of(2024, 7, 1))).isInstanceOf(TariffError.class);
    }

    @Test
    void theFirstDaysTakeTheFirstStep() {
        Tariff tariff = aTariff();
        assertThat(tariff.rateOnDay(1)).isEqualTo(new Money(10000));
        assertThat(tariff.rateOnDay(2)).isEqualTo(new Money(10000));
    }

    @Test
    void theMiddleDaysTakeTheSecondStep() {
        Tariff tariff = aTariff();
        assertThat(tariff.rateOnDay(3)).isEqualTo(new Money(15000));
        assertThat(tariff.rateOnDay(4)).isEqualTo(new Money(15000));
    }

    @Test
    void everythingAfterTakesTheLastStep() {
        Tariff tariff = aTariff();
        assertThat(tariff.rateOnDay(5)).isEqualTo(new Money(20000));
        assertThat(tariff.rateOnDay(50)).isEqualTo(new Money(20000));
    }

    @Test
    void aDayNumberedBelowOneIsRefused() {
        assertThatThrownBy(() -> aTariff().rateOnDay(0)).isInstanceOf(TariffError.class);
    }

    @Test
    void noChargeableDaysCostNothing() {
        assertThat(aTariff().charge(0)).isEqualTo(new Money(0));
    }

    @Test
    void oneChargeableDayCostsTheFirstStep() {
        assertThat(aTariff().charge(1)).isEqualTo(new Money(10000));
    }

    @Test
    void theChargeAddsTheStepsAsItClimbs() {
        assertThat(aTariff().charge(5)).isEqualTo(new Money(10000 + 10000 + 15000 + 15000 + 20000));
    }

    @Test
    void aLongHoldKeepsClimbingAtTheLastStep() {
        assertThat(aTariff().charge(6)).isEqualTo(aTariff().charge(5).plus(new Money(20000)));
    }

    @Test
    void aNegativeHoldIsRefused() {
        assertThatThrownBy(() -> aTariff().charge(-1)).isInstanceOf(TariffError.class);
    }

    @Test
    void aFlatTariffIsAllowed() {
        Tariff flat = aTariff(List.of(new Step(0, 5000)));
        assertThat(flat.charge(3)).isEqualTo(new Money(15000));
    }

    @Test
    void aBookHandsBackTheTariffInForce() {
        TariffBook book = new TariffBook();
        book.publish(aTariff());
        book.publish(aTariff("4300", LocalDate.of(2025, 7, 1)));
        assertThat(book.inEffectOn(LocalDate.of(2025, 1, 14)).code()).isEqualTo("4200");
        assertThat(book.inEffectOn(LocalDate.of(2025, 7, 1)).code()).isEqualTo("4300");
    }

    @Test
    void aBookWithNothingInForceYetIsRefused() {
        TariffBook book = new TariffBook();
        book.publish(aTariff());
        assertThatThrownBy(() -> book.inEffectOn(LocalDate.of(2024, 1, 1))).isInstanceOf(TariffError.class);
    }

    @Test
    void twoTariffsOnTheSameDayAreRefused() {
        TariffBook book = new TariffBook();
        book.publish(aTariff());
        assertThatThrownBy(() -> book.publish(aTariff("4300", LocalDate.of(2024, 7, 1))))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void aBookListsTheDatesItChangesOn() {
        TariffBook book = new TariffBook();
        book.publish(aTariff("4300", LocalDate.of(2025, 7, 1)));
        book.publish(aTariff());
        assertThat(book.effectiveDates()).containsExactly(LocalDate.of(2024, 7, 1), LocalDate.of(2025, 7, 1));
        assertThat(book.size()).isEqualTo(2);
    }
}
