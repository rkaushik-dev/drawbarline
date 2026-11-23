package io.github.rkaushikdev.drawbarline.clearance;

import static io.github.rkaushikdev.drawbarline.clearance.ClearanceChart.VALUATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClearanceChartTest {

    static final LocalDate REBUILT = LocalDate.of(2025, 6, 1);
    static final List<String> NORTH = List.of("ESC", "BKR", "PWR");
    static final Dimensions TALL = new Dimensions(Length.of(18, 0), Length.of(10, 6));

    /** The chart once the Bark River overpass was rebuilt higher. */
    static ClearanceChart rebuilt() {
        return VALUATION.filing(
                REBUILT, new Clearance("BKR", Length.of(20, 6), Length.of(12, 6), "rebuilt highway overpass"));
    }

    @Test
    void theValuationChartReadsWhatTheMapsSay() {
        assertThat(VALUATION.at("BKR", LocalDate.of(2025, 1, 14)).overhead()).isEqualTo(Length.of(17, 4));
        assertThat(VALUATION.asOf(LocalDate.of(2025, 1, 14))).isEqualTo(Clearance.CHART);
        assertThat(VALUATION.at("ESC", LocalDate.of(2025, 1, 14)).what()).isEqualTo("no restriction");
    }

    @Test
    void nothingIsFiledBeforeTheMapsWereDrawn() {
        assertThat(VALUATION.filedAt("BKR", ClearanceChart.VALUATION_DATE.minusDays(1)))
                .isEmpty();
        assertThat(VALUATION.asOf(ClearanceChart.VALUATION_DATE.minusDays(1))).isEmpty();
    }

    @Test
    void aRebuiltPlaceClearsMoreFromTheDayItWasRebuilt() {
        ClearanceChart chart = rebuilt();
        assertThat(chart.checkRoute(TALL, NORTH, REBUILT.minusDays(1))).hasSize(1);
        assertThat(chart.checkRoute(TALL, NORTH, REBUILT)).isEmpty();
        assertThat(chart.at("BKR", REBUILT).what()).isEqualTo("rebuilt highway overpass");
    }

    @Test
    void filingANewClearanceLeavesTheOldChartAlone() {
        rebuilt();
        assertThat(VALUATION.at("BKR", REBUILT).overhead()).isEqualTo(Length.of(17, 4));
        assertThat(Clearance.clearanceAt("BKR").overhead()).isEqualTo(Length.of(17, 4));
    }

    @Test
    void theGoverningPlaceMovesWhenARestrictionIsLifted() {
        ClearanceChart chart = rebuilt();
        assertThat(chart.tightestOn(NORTH, REBUILT.minusDays(1)).station()).isEqualTo("BKR");
        assertThat(chart.tightestOn(List.of("BKR", "MRN"), REBUILT).station()).isEqualTo("MRN");
        assertThat(chart.narrowestOn(List.of("BKR", "WAL"), REBUILT).station()).isEqualTo("WAL");
    }

    @Test
    void aSecondFilingAtOnePlaceOnOneDayIsRefused() {
        ClearanceChart chart = rebuilt();
        Clearance again = new Clearance("BKR", Length.of(21, 0), Length.of(12, 6));
        assertThatThrownBy(() -> chart.filing(REBUILT, again)).isInstanceOf(ClearanceError.class);
        assertThat(chart.filing(REBUILT.plusDays(1), again)
                        .at("BKR", REBUILT.plusDays(1))
                        .overhead())
                .isEqualTo(Length.of(21, 0));
    }

    @Test
    void aSlowOrderIsReadAsOfTheDayToo() {
        Dimensions close = new Dimensions(Length.of(20, 4), Length.of(10, 6));
        assertThat(rebuilt().slowOrders(close, NORTH, REBUILT))
                .containsExactly("BKR (rebuilt highway overpass) 20'6\" over 12'6\" wide, restricted speed");
        assertThat(rebuilt().checkRoute(close, NORTH, REBUILT.minusDays(1))).hasSize(1);
    }

    @Test
    void aPlaceWeDoNotServeIsRefusedOnAnyDay() {
        assertThatThrownBy(() -> VALUATION.at("HOU", REBUILT)).isInstanceOf(MileageError.class);
        assertThatThrownBy(() -> VALUATION.along(List.of(), REBUILT)).isInstanceOf(ClearanceError.class);
    }
}
