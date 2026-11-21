package io.github.rkaushikdev.drawbarline.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.distribution.CarOrder;
import io.github.rkaushikdev.drawbarline.distribution.Distribution;
import io.github.rkaushikdev.drawbarline.distribution.Empty;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DistributionReportTest {

    static final LocalDate WANTED = LocalDate.of(2025, 1, 8);

    /** Two hoppers for the mill, two for the elevator, and a box car for the elevator a day later. */
    static final List<CarOrder> ORDERS = List.of(
            new CarOrder(12, "ESCPAP", "ESCPAP1", CarKind.COVERED_HOPPER, 2, WANTED),
            new CarOrder(7, "PWRGRN", "PWRGRN1", CarKind.COVERED_HOPPER, 2, WANTED),
            new CarOrder(3, "PWRGRN", "PWRGRN1", CarKind.BOX, 1, WANTED.plusDays(1)));

    /** Three hoppers at Powers, Escanaba and Marinette, and a box car at Escanaba. */
    static final List<Empty> EMPTY_CARS = List.of(
            new Empty(CarMark.parse("CN 400100"), CarKind.COVERED_HOPPER, "PWR"),
            new Empty(CarMark.parse("CN 400101"), CarKind.COVERED_HOPPER, "ESC"),
            new Empty(CarMark.parse("DBL 104207"), CarKind.COVERED_HOPPER, "MRN"),
            new Empty(CarMark.parse("ELS 7"), CarKind.BOX, "ESC"));

    static final Map<String, String> STATIONS = Map.of("ESCPAP1", "ESC", "PWRGRN1", "PWR");

    static final List<String> SHORT_SHEET = List.of(
            "CAR DISTRIBUTION",
            "2025-01-08",
            "",
            " order  shipper  car         from  to   miles",
            "------  -------  ----------  ----  ---  -----",
            "     7  PWRGRN   CN 400100   PWR   PWR    0.0",
            "     7  PWRGRN   DBL 104207  MRN   PWR   30.5",
            "    12  ESCPAP   CN 400101   ESC   ESC    0.0",
            "     3  PWRGRN   ELS 000007  ESC   PWR   33.6",
            "------  -------  ----------  ----  ---  -----",
            "4 cars                                   64.1",
            "",
            "orders not filled",
            "   order  shipper  track    wanted      short",
            "--------  -------  -------  ----------  -----",
            "      12  ESCPAP   ESCPAP1  2025-01-08      1",
            "--------  -------  -------  ----------  -----",
            "1 orders                                    1");

    static final List<String> UNKNOWN_ORDERS = List.of(
            "CAR DISTRIBUTION",
            "no orders known",
            "",
            " order  shipper  car         from  to   miles",
            "------  -------  ----------  ----  ---  -----",
            "     7           CN 400100   PWR   PWR    0.0",
            "     7           DBL 104207  MRN   PWR   30.5",
            "    12  ESCPAP   CN 400101   ESC   ESC    0.0",
            "     3           ELS 000007  ESC   PWR   33.6",
            "------  -------  ----------  ----  ---  -----",
            "4 cars                                   64.1",
            "",
            "orders not filled",
            "   order  shipper  track    wanted      short",
            "--------  -------  -------  ----------  -----",
            "      12  ESCPAP   ESCPAP1  2025-01-08      1",
            "--------  -------  -------  ----------  -----",
            "1 orders                                    1");

    static final List<String> COMPLETE_SHEET = List.of(
            "CAR DISTRIBUTION",
            "complete",
            "",
            " order  shipper  car        from  to   miles",
            "------  -------  ---------  ----  ---  -----",
            "    12  ESCPAP   CN 400101  ESC   ESC    0.0",
            "    12  ESCPAP   CN 400100  PWR   ESC   33.6",
            "------  -------  ---------  ----  ---  -----",
            "2 cars                                  33.6",
            "",
            "every order filled");

    static final List<String> EMPTIES = List.of(
            "EMPTIES ON HAND",
            "2025-01-08",
            "",
            "station    kind            car       ",
            "---------  --------------  ----------",
            "ESC        covered hopper  CN 400101 ",
            "ESC        box car         ELS 000007",
            "MRN        covered hopper  DBL 104207",
            "PWR        covered hopper  CN 400100 ",
            "---------  --------------  ----------",
            "4 empties                            ");

    static final List<String> NO_EMPTIES = List.of(
            "EMPTIES ON HAND",
            "none",
            "",
            "station    kind  car",
            "---------  ----  ---",
            "---------  ----  ---",
            "0 empties           ");

    static Distribution distributed(List<CarOrder> orders) {
        return Distribution.distribute(orders, EMPTY_CARS, STATIONS);
    }

    @Test
    void aShortDistributionListsWhatWentWhereAndWhatDidNotGo() {
        assertThat(DistributionReport.distributionReport(distributed(ORDERS), ORDERS, "2025-01-08"))
                .containsExactlyElementsOf(SHORT_SHEET);
    }

    @Test
    void anOrderNobodyFiledPrintsBlank() {
        assertThat(DistributionReport.distributionReport(distributed(ORDERS), ORDERS.subList(0, 1), "no orders known"))
                .containsExactlyElementsOf(UNKNOWN_ORDERS);
    }

    @Test
    void aCompleteDistributionSaysEveryOrderWasFilled() {
        assertThat(DistributionReport.distributionReport(distributed(ORDERS.subList(0, 1)), ORDERS, "complete"))
                .containsExactlyElementsOf(COMPLETE_SHEET);
    }

    @Test
    void theEmptiesComeBackByStationThenCar() {
        assertThat(DistributionReport.emptiesReport(EMPTY_CARS, "2025-01-08")).containsExactlyElementsOf(EMPTIES);
    }

    @Test
    void noEmptiesStillTotals() {
        assertThat(DistributionReport.emptiesReport(List.of(), "none")).containsExactlyElementsOf(NO_EMPTIES);
    }
}
