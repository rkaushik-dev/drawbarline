package io.github.rkaushikdev.drawbarline.reports;

import static io.github.rkaushikdev.drawbarline.support.Shippers.aCustomerRegister;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.demurrage.Detention;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportDemurrageTest {

    static final Tariff TARIFF =
            new Tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000)));

    static Detention aDetention(int number) {
        return aDetention(number, 3, "ESCPAP1");
    }

    static Detention aDetention(int number, int chargeable) {
        return aDetention(number, chargeable, "ESCPAP1");
    }

    /** Placed on the 14th, counted to the 22nd, loaded and actually placed. */
    static Detention aDetention(int number, int chargeable, String industry) {
        return new Detention(
                CarMark.parse("CN " + number),
                industry,
                LocalDate.of(2025, 1, 14),
                LocalDate.of(2025, 1, 15),
                Optional.of(LocalDate.of(2025, 1, 16)),
                LocalDate.of(2025, 1, 22),
                chargeable,
                true,
                false);
    }

    /** Three days at the mill, a free car at the mill, and one day at the elevator. */
    static List<Detention> some() {
        return List.of(aDetention(1), aDetention(2, 0), aDetention(3, 1, "PWRGRN1"));
    }

    static String firstStartingWith(List<String> lines, String prefix) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theDetentionReportOpensWithAHeading() {
        List<String> lines = DemurrageReport.detentionReport(some(), TARIFF, "2025-01");
        assertThat(lines.get(0)).isEqualTo("DEMURRAGE DETENTION");
        assertThat(lines.get(1)).isEqualTo("2025-01");
    }

    @Test
    void everyCarIsOnTheDetentionReport() {
        List<String> lines = DemurrageReport.detentionReport(some(), TARIFF, "2025-01");
        assertThat(lines.stream().filter(line -> line.startsWith("CN 0000"))).hasSize(3);
    }

    @Test
    void aFreeCarShowsNoCharge() {
        List<String> lines = DemurrageReport.detentionReport(some(), TARIFF, "2025-01");
        assertThat(firstStartingWith(lines, "CN 000002")).contains("$0.00");
    }

    @Test
    void aChargedCarShowsTheTariffAmount() {
        List<String> lines = DemurrageReport.detentionReport(some(), TARIFF, "2025-01");
        assertThat(firstStartingWith(lines, "CN 000001")).contains("$400.00");
    }

    @Test
    void theDetentionReportTotalsCarsAndDays() {
        List<String> lines = DemurrageReport.detentionReport(some(), TARIFF, "2025-01");
        String total = firstStartingWith(lines, "3 cars");
        assertThat(total).contains("2 charged");
        assertThat(total).contains("$500.00");
    }

    @Test
    void theLinesComeBackByTrackThenCar() {
        List<String> lines = DemurrageReport.detentionReport(some(), TARIFF, "2025-01");
        List<String> cars = lines.stream()
                .filter(line -> line.startsWith("CN 0000"))
                .map(line -> line.split("  ")[0])
                .toList();
        assertThat(cars).containsExactly("CN 000001", "CN 000002", "CN 000003");
    }

    @Test
    void anEmptyMonthStillTotals() {
        List<String> lines = DemurrageReport.detentionReport(List.of(), TARIFF, "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("0 cars"));
    }

    @Test
    void theShipperSummaryGathersByCustomer() {
        Map<String, ShipperTotals> found = DemurrageReport.byShipper(some(), TARIFF, aCustomerRegister());
        assertThat(found.get("ESCPAP").cars()).isEqualTo(2);
        assertThat(found.get("PWRGRN").cars()).isEqualTo(1);
    }

    @Test
    void theShipperSummaryAddsTheDaysAndTheMoney() {
        Map<String, ShipperTotals> found = DemurrageReport.byShipper(some(), TARIFF, aCustomerRegister());
        assertThat(found.get("ESCPAP").days()).isEqualTo(3);
        assertThat(found.get("ESCPAP").amount()).isEqualTo(new Money(40000));
    }

    @Test
    void theShipperReportNamesTheCustomer() {
        List<String> lines = DemurrageReport.shipperReport(some(), TARIFF, aCustomerRegister(), "2025-01");
        assertThat(lines).anyMatch(line -> line.contains("Escanaba Paper"));
    }

    @Test
    void theShipperReportPutsTheBiggestBillFirst() {
        List<String> lines = DemurrageReport.shipperReport(some(), TARIFF, aCustomerRegister(), "2025-01");
        List<String> rows = lines.stream()
                .filter(line -> line.startsWith("ESCPAP") || line.startsWith("PWRGRN"))
                .toList();
        assertThat(rows.get(0)).startsWith("ESCPAP");
    }

    @Test
    void theShipperReportTotalsEveryCustomer() {
        List<String> lines = DemurrageReport.shipperReport(some(), TARIFF, aCustomerRegister(), "2025-01");
        assertThat(firstStartingWith(lines, "2 shippers")).contains("$500.00");
    }

    @Test
    void aMonthWithNoDetentionsReportsNoShippers() {
        List<String> lines = DemurrageReport.shipperReport(List.of(), TARIFF, aCustomerRegister(), "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("0 shippers"));
    }

    @Test
    void theFreeAndChargedSplit() {
        assertThat(DemurrageReport.freeAndCharged(some())).isEqualTo(new FreeAndCharged(1, 2));
    }

    @Test
    void nothingAtAllSplitsToNothing() {
        assertThat(DemurrageReport.freeAndCharged(List.of())).isEqualTo(new FreeAndCharged(0, 0));
    }

    @Test
    void noLineRunsPastThePaper() {
        for (String line : DemurrageReport.detentionReport(some(), TARIFF, "2025-01")) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }
}
