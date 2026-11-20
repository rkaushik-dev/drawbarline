package io.github.rkaushikdev.drawbarline.carhire;

import static io.github.rkaushikdev.drawbarline.carhire.Accrual.asReadAt;
import static io.github.rkaushikdev.drawbarline.carhire.Accrual.cutPoints;
import static io.github.rkaushikdev.drawbarline.carhire.Accrual.hourly;
import static io.github.rkaushikdev.drawbarline.carhire.Accrual.midnightOn;
import static io.github.rkaushikdev.drawbarline.carhire.Accrual.mileage;
import static io.github.rkaushikdev.drawbarline.carhire.Accrual.pieces;
import static io.github.rkaushikdev.drawbarline.carhire.Accrual.settlementDate;
import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccrualTest {

    static final CarTypeCode HOPPER = new CarTypeCode("L351");

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    static Car aCar() {
        return aCar(date(2020, 6, 1));
    }

    static Car aCar(LocalDate built) {
        return new Car(
                CarMark.parse("DBL 104207"),
                new MechanicalDesignation("LO"),
                HOPPER,
                built,
                new Weight(63500),
                new Weight(286000),
                Length.of(59, 6));
    }

    static RateTable aTable() {
        RateTable table = new RateTable();
        table.add(new Band(HOPPER, 0, 9, date(2024, 7, 1), new Rate(4630, 1210)));
        table.add(new Band(HOPPER, 10, 19, date(2024, 7, 1), new Rate(3180, 1210)));
        table.add(new Band(HOPPER, 0, 9, date(2025, 7, 1), new Rate(4880, 1290)));
        table.add(new Band(HOPPER, 10, 19, date(2025, 7, 1), new Rate(3400, 1290)));
        // The old cars are on one flat band, which is what the schedule does once a car is past
        // twenty.
        table.add(new Band(HOPPER, 20, 99, date(2024, 7, 1), new Rate(1470, 1210)));
        return table;
    }

    static Car aCondemnedCar() {
        return aCar(date(1970, 1, 1));
    }

    @Test
    void theAccountingDayRunsOnCentralTime() {
        assertThat(settlementDate(utc(2025, 1, 14, 5, 30))).isEqualTo(date(2025, 1, 13));
    }

    @Test
    void midnightIsAnInstantNotADate() {
        assertThat(midnightOn(date(2025, 1, 14))).isEqualTo(utc(2025, 1, 14, 6, 0));
    }

    @Test
    void aSpanInsideOneRateIsOnePiece() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 16, 12));
        assertThat(pieces(aCar(), aTable(), window)).hasSize(1);
    }

    @Test
    void aPieceCarriesTheRateInForce() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 16, 12));
        assertThat(pieces(aCar(), aTable(), window).get(0).rate()).isEqualTo(new Rate(4630, 1210));
    }

    @Test
    void anEmptySpanHasNoPieces() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 12));
        assertThat(pieces(aCar(), aTable(), window)).isEmpty();
    }

    @Test
    void aSpanOverARateChangeIsCut() {
        Interval window = new Interval(utc(2025, 6, 30, 12), utc(2025, 7, 2, 12));
        assertThat(cutPoints(aCar(), aTable(), window)).containsExactly(date(2025, 7, 1));
    }

    @Test
    void theTwoPiecesTakeTheTwoRates() {
        Interval window = new Interval(utc(2025, 6, 30, 12), utc(2025, 7, 2, 12));
        assertThat(pieces(aCar(), aTable(), window))
                .extracting(Piece::rate)
                .containsExactly(new Rate(4630, 1210), new Rate(4880, 1290));
    }

    @Test
    void thePiecesAddUpToTheWholeSpan() {
        Interval window = new Interval(utc(2025, 6, 30, 12), utc(2025, 7, 2, 12));
        List<Piece> found = pieces(aCar(), aTable(), window);
        assertThat(found.stream().mapToLong(piece -> piece.interval().minutes()).sum())
                .isEqualTo(window.minutes());
    }

    @Test
    void aBirthdayInsideASpanCutsItToo() {
        Car car = aCar(date(2015, 6, 15));
        Interval window = new Interval(utc(2025, 6, 14, 12), utc(2025, 6, 16, 12));
        assertThat(cutPoints(car, aTable(), window)).containsExactly(date(2025, 6, 15));
    }

    @Test
    void theBirthdayStepsTheRateDownABand() {
        Car car = aCar(date(2015, 6, 15));
        Interval window = new Interval(utc(2025, 6, 14, 12), utc(2025, 6, 16, 12));
        assertThat(pieces(car, aTable(), window))
                .extracting(Piece::rate)
                .containsExactly(new Rate(4630, 1210), new Rate(3180, 1210));
    }

    @Test
    void aLeapDayCarHasABirthdayEveryYear() {
        Car car = aCar(date(2016, 2, 29));
        Interval window = new Interval(utc(2025, 2, 27, 12), utc(2025, 3, 2, 12));
        assertThat(cutPoints(car, aTable(), window)).containsExactly(date(2025, 2, 28));
    }

    @Test
    void aCutExactlyOnTheSpanStartIsNotACut() {
        // July is daylight time, so midnight at the general office is 05:00 utc.
        Interval window = new Interval(utc(2025, 7, 1, 5), utc(2025, 7, 2, 12));
        assertThat(cutPoints(aCar(), aTable(), window)).isEmpty();
    }

    @Test
    void aCutExactlyOnTheSpanEndIsNotACut() {
        Interval window = new Interval(utc(2025, 6, 29, 12), utc(2025, 7, 1, 5));
        assertThat(cutPoints(aCar(), aTable(), window)).isEmpty();
    }

    @Test
    void oneHourAtTheTopRateIsTheRateInCents() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13));
        assertThat(hourly(aCar(), aTable(), window)).isEqualTo(new Money(463));
    }

    @Test
    void aDayIsTwentyFourTimesAnHour() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 15, 12));
        assertThat(hourly(aCar(), aTable(), window)).isEqualTo(new Money(463 * 24));
    }

    @Test
    void halfAnHourIsHalfTheRate() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 12, 30));
        assertThat(hourly(aCar(), aTable(), window)).isEqualTo(new Money(232));
    }

    @Test
    void anEmptySpanCostsNothing() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 12));
        assertThat(hourly(aCar(), aTable(), window)).isEqualTo(new Money(0));
    }

    @Test
    void roundingHappensOnceOnThePieceNotOnEachHour() {
        Interval tenMinutes = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 12, 10));
        Interval sixOfThem = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13));
        assertThat(hourly(aCar(), aTable(), sixOfThem))
                .isNotEqualTo(hourly(aCar(), aTable(), tenMinutes).times(6));
    }

    @Test
    void aPiecePrintsItsDayHoursAndMoney() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13));
        assertThat(pieces(aCar(), aTable(), window).get(0)).hasToString("2025-01-14 1.00 hours $4.63");
    }

    @Test
    void aHundredMilesIsAHundredTimesTheMileageRate() {
        assertThat(mileage(aCar(), aTable(), 1000, date(2025, 1, 14))).isEqualTo(new Money(12100));
    }

    @Test
    void aTenthOfAMileIsATenthOfTheRate() {
        assertThat(mileage(aCar(), aTable(), 1, date(2025, 1, 14))).isEqualTo(new Money(12));
    }

    @Test
    void noMilesCostsNothing() {
        assertThat(mileage(aCar(), aTable(), 0, date(2025, 1, 14))).isEqualTo(new Money(0));
    }

    @Test
    void negativeMilesAreRefused() {
        assertThatThrownBy(() -> mileage(aCar(), aTable(), -1, date(2025, 1, 14)))
                .isInstanceOf(RateError.class);
    }

    @Test
    void mileageFollowsTheRateInForce() {
        assertThat(mileage(aCar(), aTable(), 1000, date(2025, 7, 1))).isEqualTo(new Money(12900));
    }

    @Test
    void aSpanReadsBackOnAStationClock() {
        Network line = Reference.drawbarLine();
        Interval window = new Interval(utc(2025, 1, 14, 14), utc(2025, 1, 14, 18));
        assertThat(asReadAt(window, line.station("ESC"))).isEqualTo("2025-01-14 09:00 to 2025-01-14 13:00");
    }

    @Test
    void theSameSpanReadsAnHourEarlierAcrossTheSeam() {
        Network line = Reference.drawbarLine();
        Interval window = new Interval(utc(2025, 1, 14, 14), utc(2025, 1, 14, 18));
        assertThat(asReadAt(window, line.station("MRN"))).startsWith("2025-01-14 08:00");
    }

    @Test
    void aCondemnedCarIsPricedAtNothing() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 16, 12));
        assertThat(hourly(aCondemnedCar(), aTable(), window)).isEqualTo(new Money(0));
    }

    @Test
    void aCondemnedCarProducesNoPieces() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 16, 12));
        assertThat(pieces(aCondemnedCar(), aTable(), window)).isEmpty();
    }

    @Test
    void aCondemnedCarRunsNoChargeableMiles() {
        assertThat(mileage(aCondemnedCar(), aTable(), 1000, date(2025, 1, 14))).isEqualTo(new Money(0));
    }

    @Test
    void aSpanThatCrossesTheExpiryOnlyPricesTheLivePart() {
        Car car = aCar(date(1975, 6, 15));
        Interval window = new Interval(utc(2025, 6, 14, 12), utc(2025, 6, 16, 12));
        List<Piece> found = pieces(car, aTable(), window);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).on()).isEqualTo(date(2025, 6, 14));
    }

    @Test
    void anElectionLowersTheHourlyCharge() {
        RateTable table = aTable();
        Elections elections = new Elections();
        elections.file(new Election(HOPPER, 0, 9, date(2024, 7, 1), 3900), table);
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13));
        assertThat(hourly(aCar(), table, window, elections)).isEqualTo(new Money(390));
    }

    @Test
    void anElectionLeavesTheMileageAlone() {
        RateTable table = aTable();
        Elections elections = new Elections();
        elections.file(new Election(HOPPER, 0, 9, date(2024, 7, 1), 3900), table);
        assertThat(mileage(aCar(), table, 1000, date(2025, 1, 14), elections)).isEqualTo(new Money(12100));
    }

    @Test
    void anAccountingDayIsAsLongAsTheGeneralOfficeClockSaysItIs() {
        // The autumn day the clocks go back runs twenty five hours and the spring one twenty three,
        // and a car standing the whole day earns every one of them.
        Interval autumn = new Interval(midnightOn(date(2025, 11, 2)), midnightOn(date(2025, 11, 3)));
        Interval spring = new Interval(midnightOn(date(2025, 3, 9)), midnightOn(date(2025, 3, 10)));
        assertThat(autumn.minutes()).isEqualTo(25 * 60);
        // November is past the July step, so the hour is 488 cents there.
        assertThat(hourly(aCar(), aTable(), autumn)).isEqualTo(new Money(488 * 25));
        assertThat(spring.minutes()).isEqualTo(23 * 60);
        assertThat(hourly(aCar(), aTable(), spring)).isEqualTo(new Money(463 * 23));
        assertThat(settlementDate(autumn.end().minusSeconds(1))).isEqualTo(date(2025, 11, 2));
    }

    @Test
    void noElectionPricesAtTheSchedule() {
        Interval window = new Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13));
        assertThat(hourly(aCar(), aTable(), window, new Elections())).isEqualTo(new Money(463));
    }
}
