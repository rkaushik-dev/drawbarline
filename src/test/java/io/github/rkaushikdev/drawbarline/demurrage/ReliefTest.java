package io.github.rkaushikdev.drawbarline.demurrage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.customers.Shipper;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReliefTest {

    static final Tariff TARIFF =
            new Tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000)));
    static final Shipper MILL = new Shipper("ESCPAP", "Escanaba Paper");
    static final Industry TRACK = new Industry("ESCPAP1", "mill house track", "ESC", MILL, 4);
    static final LocalDate PLACED = LocalDate.of(2025, 1, 14);

    static Detention aDetention(int number) {
        return aDetention(number, PLACED, "ESCPAP1", false);
    }

    static Detention aDetention(int number, String industry) {
        return aDetention(number, PLACED, industry, false);
    }

    static Detention aDetention(int number, LocalDate placedOn) {
        return aDetention(number, placedOn, "ESCPAP1", false);
    }

    static Detention aConstructiveDetention(int number) {
        return aDetention(number, PLACED, "ESCPAP1", true);
    }

    static Detention aDetention(int number, LocalDate placedOn, String industry, boolean constructive) {
        return new Detention(
                CarMark.parse("CN " + number),
                industry,
                placedOn,
                LocalDate.of(2025, 1, 15),
                Optional.of(LocalDate.of(2025, 1, 16)),
                LocalDate.of(2025, 1, 22),
                4,
                true,
                constructive);
    }

    static List<Detention> numbered(int first, int last) {
        return IntStream.rangeClosed(first, last)
                .mapToObj(ReliefTest::aDetention)
                .toList();
    }

    @Test
    void noReliefLeavesTheDetentionAlone() {
        Detention one = aDetention(1);
        assertThat(Relief.recount(one, TARIFF, 0)).isSameAs(one);
    }

    @Test
    void reliefPushesTheClockForward() {
        Detention moved = Relief.recount(aDetention(1), TARIFF, 2);
        assertThat(moved.clockStarts()).isEqualTo(LocalDate.of(2025, 1, 17));
    }

    @Test
    void reliefPushesTheFreeTimeWithIt() {
        Detention moved = Relief.recount(aDetention(1), TARIFF, 2);
        assertThat(moved.freeUntil()).contains(LocalDate.of(2025, 1, 20));
    }

    @Test
    void reliefTakesDaysOffTheCharge() {
        assertThat(Relief.recount(aDetention(1), TARIFF, 2).chargeable()).isEqualTo(2);
    }

    @Test
    void enoughReliefTakesTheChargeToNothing() {
        assertThat(Relief.recount(aDetention(1), TARIFF, 10).chargeable()).isZero();
    }

    @Test
    void reliefWalksTheCalendarNotTheWeek() {
        Detention moved = Relief.recount(aDetention(1), TARIFF, 3);
        assertThat(moved.clockStarts()).isEqualTo(LocalDate.of(2025, 1, 20));
    }

    @Test
    void negativeReliefIsRefused() {
        assertThatThrownBy(() -> Relief.recount(aDetention(1), TARIFF, -1)).isInstanceOf(TariffError.class);
    }

    @Test
    void aDeliveryInsideTheWorkingRateGetsNoRelief() {
        assertThat(Relief.bunching(numbered(1, 4), 4)).isEmpty();
    }

    @Test
    void theCarsPastTheWorkingRateArePushedBack() {
        assertThat(Relief.bunching(numbered(1, 8), 4)).extracting(Relief::days).containsExactly(1, 1, 1, 1);
    }

    @Test
    void aSecondBatchIsPushedBackFurther() {
        List<Relief> found = Relief.bunching(numbered(1, 12), 4);
        assertThat(new TreeSet<>(found.stream().map(Relief::days).toList())).containsExactly(1, 2);
    }

    @Test
    void reliefIsHandedOutInCarOrderSoTwoRunsAgree() {
        List<Detention> cars = new ArrayList<>();
        for (int number : new int[] {7, 3, 5, 1, 9, 2}) {
            cars.add(aDetention(number));
        }
        List<String> first = Relief.bunching(cars, 2).stream()
                .map(one -> one.car().toString())
                .toList();
        List<String> second = Relief.bunching(cars.reversed(), 2).stream()
                .map(one -> one.car().toString())
                .toList();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void twoTracksAreWorkedIndependently() {
        List<Detention> cars = new ArrayList<>(numbered(1, 4));
        IntStream.rangeClosed(10, 13).forEach(number -> cars.add(aDetention(number, "ESCPAP2")));
        assertThat(Relief.bunching(cars, 4)).isEmpty();
    }

    @Test
    void twoDaysOfDeliveriesAreCountedApart() {
        List<Detention> cars = new ArrayList<>(numbered(1, 4));
        IntStream.rangeClosed(10, 13).forEach(number -> cars.add(aDetention(number, LocalDate.of(2025, 1, 15))));
        assertThat(Relief.bunching(cars, 4)).isEmpty();
    }

    @Test
    void aCustomerWhoWorksNoCarsIsRefused() {
        assertThatThrownBy(() -> Relief.bunching(List.of(aDetention(1)), 0)).isInstanceOf(TariffError.class);
    }

    @Test
    void reliefPrintsTheCarAndTheDays() {
        Relief relief = new Relief(CarMark.parse("CN 000001"), "ESCPAP1", 2);
        assertThat(relief).hasToString("CN 000001 2 days relief for bunching");
    }

    @Test
    void relievingASetRecountsTheOnesThatEarnedIt() {
        List<Detention> relieved = Relief.relieve(numbered(1, 8), TARIFF, 4);
        assertThat(relieved.stream().map(Detention::chargeable).sorted().toList())
                .containsExactly(3, 3, 3, 3, 4, 4, 4, 4);
    }

    @Test
    void relievingASetThatEarnedNoneChangesNothing() {
        assertThat(Relief.relieve(numbered(1, 4), TARIFF, 4))
                .extracting(Detention::chargeable)
                .containsExactly(4, 4, 4, 4);
    }

    @Test
    void aFullTrackSupportsAConstructivePlacement() {
        assertThat(Relief.constructiveStands(TRACK, 4)).isTrue();
    }

    @Test
    void aTrackWithRoomDoesNot() {
        assertThat(Relief.constructiveStands(TRACK, 3)).isFalse();
    }

    @Test
    void aConstructivePlacementOntoATrackWithRoomIsRefused() {
        Detention one = aConstructiveDetention(1);
        assertThatThrownBy(() -> Relief.checkConstructive(one, TRACK, 2)).isInstanceOf(TariffError.class);
    }

    @Test
    void aConstructivePlacementOntoAFullTrackStands() {
        Detention one = aConstructiveDetention(1);
        assertThat(Relief.checkConstructive(one, TRACK, 4)).isSameAs(one);
    }

    @Test
    void anActualPlacementIsNeverQuestioned() {
        Detention one = aDetention(1);
        assertThat(Relief.checkConstructive(one, TRACK, 0)).isSameAs(one);
    }
}
