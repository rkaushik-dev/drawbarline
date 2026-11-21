package io.github.rkaushikdev.drawbarline.reporting;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.reports.Crossing;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.support.Cars;
import io.github.rkaushikdev.drawbarline.support.Filings;
import io.github.rkaushikdev.drawbarline.support.Moves;
import io.github.rkaushikdev.drawbarline.support.Shippers;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The reporting layer is what both interfaces call, so it is tested directly.
 *
 * <p>The property that matters is that the command line and the HTTP side get the same lines.
 * Testing that through both would be testing the two frameworks, so the layer is tested here and
 * each interface is tested for calling it.
 */
class ReportingTest {

    private static final Period JANUARY = new Period(2025, 1);

    private Database worked;

    /** One car taken in, run to the mill, spotted, released, pulled and handed back in January. */
    @BeforeEach
    void workAMonth() {
        worked = TestDatabase.seeded(work -> {
            work.customers().addShipper(Shippers.MILL);
            work.customers().addShipper(Shippers.ELEVATOR);
            work.customers().addIndustry(new Industry("ESCPAP1", "mill house", "ESC", Shippers.MILL, 11));
            work.tariffs()
                    .publish(new Tariff(
                            "4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000))));
            for (Band band : Filings.someBands()) {
                work.rates().file(band);
            }
            work.fleet().add(Cars.aCar("CN 400100"));
        });
        try (UnitOfWork work = worked.unitOfWork()) {
            EventLog log = work.events().log();
            Moves.received(log, "CN 400100", 6);
            Moves.moved(log, "CN 400100", 6, 336);
            Moves.placed(log, "CN 400100", 7, "ESCPAP1");
            Moves.released(log, "CN 400100", 14, "ESCPAP1");
            Moves.pulled(log, "CN 400100", 15, "ESCPAP1");
            Moves.delivered(log, "CN 400100", 16);
            for (CarMark car : log.cars()) {
                for (Event event : log.stream(car)) {
                    work.events().record(event);
                }
            }
            work.commit();
        }
    }

    private <T> T linesOf(Function<UnitOfWork, T> call) {
        try (UnitOfWork work = worked.unitOfWork()) {
            return call.apply(work);
        }
    }

    @Test
    void theCarHireReportNamesTheRoad() {
        List<String> found = linesOf(work -> Reporting.carHire(work, JANUARY));
        assertThat(found.get(0)).isEqualTo("CAR HIRE LIABILITY");
        assertThat(found).anyMatch(line -> line.startsWith("CN"));
    }

    @Test
    void theCarHireDetailCanBeAskedForOneRoad() {
        List<String> found = linesOf(work -> Reporting.carHire(work, JANUARY, "CN"));
        assertThat(found.get(0)).isEqualTo("CAR HIRE DETAIL CN");
    }

    @Test
    void theTotalsAgreeWithThePage() {
        List<String> page = linesOf(work -> Reporting.carHire(work, JANUARY));
        Map<String, Money> totals = linesOf(work -> Reporting.carHireTotals(work, JANUARY));
        String row =
                page.stream().filter(line -> line.startsWith("CN")).findFirst().orElseThrow();
        assertThat(row).contains(totals.get("CN").toString());
    }

    @Test
    void theTotalsComeBackAsMoney() {
        Map<String, Money> totals = linesOf(work -> Reporting.carHireTotals(work, JANUARY));
        assertThat(totals.get("CN")).isInstanceOf(Money.class);
    }

    @Test
    void theDemurrageReportRuns() {
        List<String> found = linesOf(work -> Reporting.demurrage(work, JANUARY));
        assertThat(found.get(0)).isEqualTo("DEMURRAGE DETENTION");
    }

    @Test
    void theDemurrageReportSummarisesByShipper() {
        List<String> found = linesOf(work -> Reporting.demurrage(work, JANUARY, true));
        assertThat(found.get(0)).isEqualTo("DEMURRAGE BY SHIPPER");
    }

    @Test
    void theUtilisationReportRuns() {
        List<String> found = linesOf(work -> Reporting.utilisation(work, JANUARY));
        assertThat(found).anyMatch(line -> line.contains("CN 400100"));
    }

    @Test
    void theInterchangeReportRuns() {
        List<String> found = linesOf(work -> Reporting.interchange(work, JANUARY));
        assertThat(found.get(0)).isEqualTo("INTERCHANGE BALANCE");
    }

    @Test
    void theCrossingsComeBackAsData() {
        List<Crossing> found = linesOf(work -> Reporting.interchangeCrossings(work, JANUARY));
        assertThat(found.stream().map(Crossing::junction).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("ESC", "MRN");
    }

    @Test
    void theOnHandReportRuns() {
        List<String> found = linesOf(work -> Reporting.onHand(work, utc(2025, 1, 10, 12)));
        assertThat(found).anyMatch(line -> line.contains("CN 400100"));
    }

    @Test
    void aCarAlreadyDeliveredIsNotOnHand() {
        List<String> found = linesOf(work -> Reporting.onHand(work, utc(2025, 1, 20, 12)));
        assertThat(found).anyMatch(line -> line.startsWith("0 cars"));
    }

    @Test
    void theAgeReportRuns() {
        List<String> found = linesOf(work -> Reporting.fleetAge(work, LocalDate.of(2025, 7, 1)));
        assertThat(found.get(0)).isEqualTo("FLEET AGE PROFILE");
    }

    @Test
    void theAgeProfileComesBackAsData() {
        Map<String, Integer> found = linesOf(work -> Reporting.fleetAgeProfile(work, LocalDate.of(2025, 7, 1)));
        assertThat(found).isEqualTo(Map.of("0 to 9", 1));
    }

    @Test
    void theRepairReportRunsWithNoCards() {
        List<String> found = linesOf(work -> Reporting.repairs(work, JANUARY));
        assertThat(found).anyMatch(line -> line.startsWith("0 roads"));
    }

    @Test
    void theExceptionReportRunsWithNothingOpen() {
        List<String> found = linesOf(work -> Reporting.repairs(work, JANUARY, true));
        assertThat(found).anyMatch(line -> line.startsWith("0 open"));
    }

    @Test
    void runningAReportTwiceGivesTheSameLines() {
        List<String> first = linesOf(work -> Reporting.carHire(work, JANUARY));
        List<String> second = linesOf(work -> Reporting.carHire(work, JANUARY));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void noReportWritesAnything() {
        int before = linesOf(work -> work.events().size());
        linesOf(work -> Reporting.carHire(work, JANUARY));
        linesOf(work -> Reporting.demurrage(work, JANUARY));
        int after = linesOf(work -> work.events().size());
        assertThat(after).isEqualTo(before);
    }

    @Test
    void everyLineFitsThePaper() {
        for (String line : linesOf(work -> Reporting.carHire(work, JANUARY))) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }
}
