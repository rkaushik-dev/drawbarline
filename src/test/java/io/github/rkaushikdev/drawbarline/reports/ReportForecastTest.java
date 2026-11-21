package io.github.rkaushikdev.drawbarline.reports;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.support.Cars.HOME;
import static io.github.rkaushikdev.drawbarline.support.Cars.aFleet;
import static io.github.rkaushikdev.drawbarline.support.Filings.aRateTable;
import static io.github.rkaushikdev.drawbarline.support.Moves.delivered;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.carhire.RateTable;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportForecastTest {

    static final Instant NOW = utc(2025, 1, 10, 12);
    static final ReportingMark CN = new ReportingMark("CN");

    /** A fleet of the given cars, each received from CN on the 6th. */
    record World(FleetRegister register, EventLog log) {}

    static World aWorld(String... marks) {
        FleetRegister register = aFleet(marks);
        EventLog log = new EventLog();
        for (String mark : marks) {
            received(log, mark, 6);
        }
        return new World(register, log);
    }

    static List<StandingCar> standing(String... marks) {
        World world = aWorld(marks);
        return ForecastReport.standingCars(world.register(), world.log(), aRateTable(), NOW, HOME);
    }

    @Test
    void aForeignCarOnHandIsStanding() {
        assertThat(standing("CN 400100").stream().map(one -> one.car().toString()))
                .containsExactly("CN 400100");
    }

    @Test
    void ourOwnCarCostsUsNothing() {
        assertThat(standing("DBL 104207")).isEmpty();
    }

    @Test
    void aPrivateCarIsLeftOutBecauseItEarnsNoHours() {
        assertThat(standing("PLMX 9001")).isEmpty();
    }

    @Test
    void aDeliveredCarIsNotStanding() {
        World world = aWorld("CN 400100");
        delivered(world.log(), "CN 400100", 8);
        assertThat(ForecastReport.standingCars(world.register(), world.log(), aRateTable(), NOW, HOME))
                .isEmpty();
    }

    @Test
    void aCarWithNoEquipmentRecordIsLeftOut() {
        FleetRegister register = aFleet();
        EventLog log = new EventLog();
        received(log, "CN 400100", 6);
        assertThat(ForecastReport.standingCars(register, log, aRateTable(), NOW, HOME))
                .isEmpty();
    }

    @Test
    void aCarWithNoRateOnFileIsLeftOut() {
        World world = aWorld("CN 400100");
        assertThat(ForecastReport.standingCars(world.register(), world.log(), new RateTable(), NOW, HOME))
                .isEmpty();
    }

    @Test
    void aDayIsTheHourlyRateOverTwentyFourHours() {
        StandingCar found = standing("CN 400100").get(0);
        assertThat(found.aDay()).isEqualTo(new Money((long) Math.rint(4630 * 24 / 10.0)));
    }

    @Test
    void aCarPrintsWhereItIsAndWhatItCosts() {
        assertThat(standing("CN 400100").get(0)).hasToString("CN 400100 at ESC $111.12 a day");
    }

    @Test
    void theWholeRailroadAddsUp() {
        assertThat(ForecastReport.aDay(standing("CN 400100", "CN 400101"))).isEqualTo(new Money(2 * 11112));
    }

    @Test
    void nothingStandingCostsNothing() {
        assertThat(ForecastReport.aDay(List.of())).isEqualTo(new Money(0));
    }

    @Test
    void theBurnGathersByRoad() {
        assertThat(ForecastReport.byRoad(standing("CN 400100", "CN 400101"))).isEqualTo(Map.of(CN, new Money(22224)));
    }

    @Test
    void aRunOfDaysMultipliesTheBurn() {
        assertThat(ForecastReport.over(standing("CN 400100"), 7)).isEqualTo(new Money(11112 * 7));
    }

    @Test
    void aRunOfNoDaysCostsNothing() {
        assertThat(ForecastReport.over(standing("CN 400100"), 0)).isEqualTo(new Money(0));
    }

    @Test
    void aBackwardsForecastIsRefused() {
        List<StandingCar> cars = standing("CN 400100");
        assertThatThrownBy(() -> ForecastReport.over(cars, -1)).isInstanceOf(RateError.class);
    }

    @Test
    void theDaysToReachAnAmount() {
        assertThat(ForecastReport.daysUntil(standing("CN 400100"), new Money(50000)))
                .isEqualTo(5);
    }

    @Test
    void anAmountAlreadyReachedTakesNoDays() {
        assertThat(ForecastReport.daysUntil(standing("CN 400100"), new Money(0)))
                .isEqualTo(0);
    }

    @Test
    void nothingAccruingNeverReachesAnAmount() {
        assertThatThrownBy(() -> ForecastReport.daysUntil(List.of(), new Money(100)))
                .isInstanceOf(RateError.class);
    }

    @Test
    void theReportOpensWithAHeading() {
        List<String> lines = ForecastReport.forecastReport(standing("CN 400100"), NOW);
        assertThat(lines.get(0)).isEqualTo("CAR HIRE FORECAST");
        assertThat(lines.get(1)).contains("nothing moves");
    }

    @Test
    void theReportHasALineARoad() {
        List<String> lines = ForecastReport.forecastReport(standing("CN 400100"), NOW);
        assertThat(lines).anyMatch(line -> line.startsWith("CN"));
    }

    @Test
    void theReportTotalsEveryRoad() {
        List<String> lines = ForecastReport.forecastReport(standing("CN 400100", "CN 400101"), NOW);
        String total = lines.stream()
                .filter(line -> line.startsWith("all roads"))
                .findFirst()
                .orElseThrow();
        assertThat(total).contains("$222.24");
    }

    @Test
    void theReportRunsTheDaysOut() {
        List<String> lines = ForecastReport.forecastReport(standing("CN 400100"), NOW, 7);
        assertThat(lines).anyMatch(line -> line.contains("$777.84"));
    }

    @Test
    void aRailroadWithNothingStandingReportsNothing() {
        List<String> lines = ForecastReport.forecastReport(List.of(), NOW);
        assertThat(lines).anyMatch(line -> line.startsWith("all roads"));
    }

    @Test
    void everyLineFitsThePaper() {
        for (String line : ForecastReport.forecastReport(standing("CN 400100"), NOW)) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }

    @Test
    void tomorrowIsTheSameMomentADayOn() {
        assertThat(ForecastReport.tomorrow(NOW)).isEqualTo(utc(2025, 1, 11, 12));
    }
}
