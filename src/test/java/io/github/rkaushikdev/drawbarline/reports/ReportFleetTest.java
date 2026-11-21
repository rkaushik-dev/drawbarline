package io.github.rkaushikdev.drawbarline.reports;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.support.Cars.aCar;
import static io.github.rkaushikdev.drawbarline.support.Cars.aFleet;
import static io.github.rkaushikdev.drawbarline.support.Moves.delivered;
import static io.github.rkaushikdev.drawbarline.support.Moves.moved;
import static io.github.rkaushikdev.drawbarline.support.Moves.placed;
import static io.github.rkaushikdev.drawbarline.support.Moves.pulled;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static io.github.rkaushikdev.drawbarline.support.Moves.released;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ReportFleetTest {

    static final Interval JANUARY = new Interval(utc(2025, 1, 1, 6), utc(2025, 2, 1, 6));
    static final CarMark CAR = CarMark.parse("CN 400100");

    /** CN 400100 received, moved, placed on the mill track, released, pulled and delivered. */
    static EventLog aLog() {
        EventLog log = new EventLog();
        received(log, "CN 400100", 6);
        moved(log, "CN 400100", 6, 336);
        placed(log, "CN 400100", 7);
        released(log, "CN 400100", 14);
        pulled(log, "CN 400100", 15);
        delivered(log, "CN 400100", 16);
        return log;
    }

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    static Stream<String> utilisationLines() {
        return FleetReport.utilisationReport(aLog(), JANUARY, "2025-01").stream();
    }

    @Test
    void aCarThatCameAndWentShowsOneVisit() {
        assertThat(FleetReport.cycleOf(aLog(), CAR, JANUARY).visits()).isEqualTo(1);
    }

    @Test
    void theTurnTimeIsTheWholeStay() {
        assertThat(FleetReport.cycleOf(aLog(), CAR, JANUARY).onLineMinutes()).isEqualTo(10 * 24 * 60);
    }

    @Test
    void theStandingTimeIsThePartOnTheCustomerTrack() {
        assertThat(FleetReport.cycleOf(aLog(), CAR, JANUARY).onTrackMinutes()).isEqualTo(8 * 24 * 60 + 2 * 60);
    }

    @Test
    void theMovingTimeIsWhatIsLeft() {
        Cycle found = FleetReport.cycleOf(aLog(), CAR, JANUARY);
        assertThat(found.movingMinutes()).isEqualTo(found.onLineMinutes() - found.onTrackMinutes());
    }

    @Test
    void theTurnTimeReadsInHours() {
        assertThat(FleetReport.cycleOf(aLog(), CAR, JANUARY).turnHundredths()).isEqualTo(24000);
    }

    @Test
    void theMilesComeOffTheMovements() {
        assertThat(FleetReport.cycleOf(aLog(), CAR, JANUARY).tenthsRun()).isEqualTo(336);
    }

    @Test
    void aCarThatNeverCameHasNoCycle() {
        assertThat(FleetReport.cycleOf(new EventLog(), CAR, JANUARY).onLineMinutes())
                .isEqualTo(0);
    }

    @Test
    void carsWithNothingInTheWindowAreLeftOut() {
        assertThat(FleetReport.cycles(new EventLog(), JANUARY)).isEmpty();
    }

    @Test
    void theCyclesCoverEveryCarThatMoved() {
        assertThat(FleetReport.cycles(aLog(), JANUARY).stream().map(Cycle::car)).containsExactly(CAR);
    }

    @Test
    void theReportOpensWithAHeading() {
        List<String> lines = FleetReport.utilisationReport(aLog(), JANUARY, "2025-01");
        assertThat(lines.get(0)).isEqualTo("FLEET UTILISATION");
    }

    @Test
    void theReportHasALineACar() {
        List<String> lines = FleetReport.utilisationReport(aLog(), JANUARY, "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("CN 400100"));
    }

    @Test
    void theReportPrintsTheTurnAndStandingHours() {
        List<String> lines = FleetReport.utilisationReport(aLog(), JANUARY, "2025-01");
        String row = lines.stream()
                .filter(line -> line.startsWith("CN 400100"))
                .findFirst()
                .orElseThrow();
        assertThat(row).contains("240.00");
        assertThat(row).contains("194.00");
    }

    @Test
    void theReportTotalsTheCars() {
        List<String> lines = FleetReport.utilisationReport(aLog(), JANUARY, "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("1 cars"));
    }

    @Test
    void anEmptyLogReportsNoCars() {
        List<String> lines = FleetReport.utilisationReport(new EventLog(), JANUARY, "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("0 cars"));
    }

    @Test
    void theAgeProfileCountsByDecade() {
        FleetRegister register = aFleet();
        register.add(aCar("DBL 104207", date(2020, 6, 1)));
        register.add(aCar("DBL 104208", date(2010, 6, 1)));
        register.add(aCar("CN 400100", date(2009, 6, 1)));
        assertThat(FleetReport.ageProfile(register, date(2025, 7, 1))).isEqualTo(Map.of("0 to 9", 1, "10 to 19", 2));
    }

    @Test
    void aRetiredCarIsLeftOutOfTheProfile() {
        FleetRegister register = aFleet();
        register.add(aCar("DBL 104207", date(2020, 6, 1)));
        register.retire(CarMark.parse("DBL 104207"), date(2024, 12, 31));
        assertThat(FleetReport.ageProfile(register, date(2025, 7, 1))).isEmpty();
    }

    @Test
    void theCarsRunningOutOfLifeComeBack() {
        FleetRegister register = aFleet();
        register.add(aCar("DBL 104207", date(1978, 6, 1)));
        register.add(aCar("DBL 104208", date(2020, 6, 1)));
        List<CarMark> found = FleetReport.expiringWithin(register, date(2025, 7, 1), 5);
        assertThat(found.stream().map(CarMark::toString)).containsExactly("DBL 104207");
    }

    @Test
    void nothingExpiringComesBackEmpty() {
        FleetRegister register = aFleet();
        register.add(aCar("DBL 104208", date(2020, 6, 1)));
        assertThat(FleetReport.expiringWithin(register, date(2025, 7, 1), 5)).isEmpty();
    }

    @Test
    void theAgeReportCarriesTheProfileAndTheList() {
        FleetRegister register = aFleet();
        register.add(aCar("DBL 104207", date(1978, 6, 1)));
        register.add(aCar("DBL 104208", date(2020, 6, 1)));
        List<String> lines = FleetReport.ageReport(register, date(2025, 7, 1));
        assertThat(lines.get(0)).isEqualTo("FLEET AGE PROFILE");
        assertThat(lines).anyMatch(line -> line.startsWith("all cars"));
        assertThat(lines).anyMatch(line -> line.contains("within 5 years: 1"));
        assertThat(lines).anyMatch(line -> Text.strip(line).equals("DBL 104207"));
    }

    @Test
    void theAgeReportOnAnEmptyFleetTotalsNothing() {
        List<String> lines = FleetReport.ageReport(aFleet(), date(2025, 7, 1));
        String total = lines.stream()
                .filter(line -> line.startsWith("all cars"))
                .findFirst()
                .orElseThrow();
        String[] words = Text.strip(total).split("\\s+");
        assertThat(words[words.length - 1]).isEqualTo("0");
    }

    @ParameterizedTest
    @MethodSource("utilisationLines")
    void noLineRunsPastThePaper(String line) {
        assertThat(line.length()).isLessThanOrEqualTo(80);
    }
}
