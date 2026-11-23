package io.github.rkaushikdev.drawbarline.clearance;

import static io.github.rkaushikdev.drawbarline.clearance.Clearance.CHART;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClearanceTest {

    static final Dimensions ORDINARY = new Dimensions(Length.of(15, 4), Length.of(10, 6));
    static final Dimensions TALL = new Dimensions(Length.of(18, 0), Length.of(10, 6));
    static final Dimensions WIDE = new Dimensions(Length.of(15, 0), Length.of(12, 4));
    static final Dimensions HUGE = new Dimensions(Length.of(21, 6), Length.of(13, 2));

    @Test
    void thePlatesClimbInHeight() {
        assertThat(Arrays.stream(Plate.values())
                        .map(plate -> plate.height().inches())
                        .toList())
                .isSorted();
    }

    @Test
    void aPlateHoldsWhatFitsInsideIt() {
        assertThat(Plate.C.holds(ORDINARY)).isTrue();
        assertThat(Plate.B.holds(ORDINARY)).isFalse();
    }

    @Test
    void aLoadFindsTheSmallestPlateThatHoldsIt() {
        assertThat(ORDINARY.smallestPlate()).contains(Plate.C);
    }

    @Test
    void aShortLoadTakesTheSmallestPlate() {
        assertThat(new Dimensions(Length.of(14, 0), Length.of(10, 0)).smallestPlate())
                .contains(Plate.B);
    }

    @Test
    void aTallLoadTakesABiggerPlate() {
        assertThat(TALL.smallestPlate()).contains(Plate.H);
    }

    @Test
    void aLoadOutsideEveryPlateIsDimensional() {
        assertThat(HUGE.smallestPlate()).isEmpty();
        assertThat(HUGE.dimensional()).isTrue();
    }

    @Test
    void anOrdinaryLoadIsNotDimensional() {
        assertThat(ORDINARY.dimensional()).isFalse();
    }

    @Test
    void aWideLoadIsDimensionalHoweverShort() {
        assertThat(WIDE.dimensional()).isTrue();
    }

    @Test
    void aLoadPrintsItsTwoDimensions() {
        assertThat(ORDINARY).hasToString("15'4\" high 10'6\" wide");
    }

    @Test
    void aLoadOfNoHeightIsRefused() {
        assertThatThrownBy(() -> new Dimensions(new Length(0), Length.of(10, 0)))
                .isInstanceOf(ClearanceError.class);
    }

    @Test
    void aClearancePrintsWhatItIs() {
        assertThat(CHART.get("BKR")).hasToString("BKR (highway overpass) 17'4\" over 12'6\" wide");
    }

    @Test
    void aClearanceAtAPlaceWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> new Clearance("CHI", Length.of(20, 0), Length.of(12, 0)))
                .isInstanceOf(MileageError.class);
    }

    @Test
    void aStationWithNothingFiledReadsAsOpen() {
        Clearance found = Clearance.clearanceAt("PWR");
        assertThat(found.what()).isEqualTo("no restriction");
        assertThat(found.clears(TALL)).isTrue();
    }

    @Test
    void aStationWithARestrictionReadsItBack() {
        assertThat(Clearance.clearanceAt("BKR").overhead()).isEqualTo(Length.of(17, 4));
    }

    @Test
    void askingAboutAPlaceWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> Clearance.clearanceAt("CHI")).isInstanceOf(MileageError.class);
    }

    @Test
    void anOrdinaryLoadClearsTheWholeRailroad() {
        assertThat(Clearance.clearsRoute(ORDINARY, List.of("ESC", "BKR", "PWR", "GRB")))
                .isTrue();
    }

    @Test
    void aTallLoadDoesNotClearBarkRiver() {
        List<String> found = Clearance.checkRoute(TALL, List.of("ESC", "BKR", "PWR"));
        assertThat(found).hasSize(1);
        assertThat(found.get(0)).startsWith("BKR clears");
    }

    @Test
    void aWideLoadDoesNotClearWallace() {
        List<String> found = Clearance.checkRoute(WIDE, List.of("PWR", "WAL", "MEN"));
        assertThat(found).anySatisfy(one -> assertThat(one).contains("wide"));
    }

    @Test
    void aLoadThatIsBothDrawsTwoFindingsAtOnePlace() {
        assertThat(Clearance.checkRoute(HUGE, List.of("BKR"))).hasSize(2);
    }

    @Test
    void aRouteAvoidingTheRestrictionClears() {
        assertThat(Clearance.clearsRoute(TALL, List.of("PWR", "SPL", "MEN"))).isTrue();
    }

    @Test
    void theTightestPlaceOnARouteIsTheLowestOverhead() {
        assertThat(Clearance.tightestOn(List.of("ESC", "BKR", "MRN")).station()).isEqualTo("BKR");
    }

    @Test
    void aRouteWithNoRestrictionReportsAnOpenClearance() {
        assertThat(Clearance.tightestOn(List.of("ESC", "PWR")).what()).isEqualTo("no restriction");
    }

    @Test
    void anEmptyRouteIsRefused() {
        assertThatThrownBy(() -> Clearance.tightestOn(List.of())).isInstanceOf(ClearanceError.class);
    }

    @Test
    void checkingAnEmptyRouteIsRefused() {
        assertThatThrownBy(() -> Clearance.checkRoute(ORDINARY, List.of())).isInstanceOf(ClearanceError.class);
    }

    @Test
    void everyFiledClearanceIsAtAStationWeServe() {
        CHART.forEach((station, one) -> assertThat(one.station()).isEqualTo(station));
    }

    @Test
    void aLoadExactlyOnTheClearancePasses() {
        Dimensions exact = new Dimensions(CHART.get("BKR").overhead(), Length.of(10, 0));
        assertThat(Clearance.clearsRoute(exact, List.of("BKR"))).isTrue();
    }

    @Test
    void aLoadOneInchOverDoesNot() {
        Dimensions over = new Dimensions(CHART.get("BKR").overhead().plus(new Length(1)), Length.of(10, 0));
        assertThat(Clearance.clearsRoute(over, List.of("BKR"))).isFalse();
    }

    @Test
    void theNarrowestPlaceOnARouteIsNotTheLowest() {
        List<String> route = List.of("ESC", "BKR", "PWR", "WAL", "MEN");
        assertThat(Clearance.tightestOn(route).station()).isEqualTo("BKR");
        assertThat(Clearance.narrowestOn(route).station()).isEqualTo("WAL");
        assertThat(Clearance.narrowestOn(route).side()).isEqualTo(Length.of(11, 2));
    }

    @Test
    void aTieGoesToThePlaceReachedFirst() {
        assertThat(Clearance.narrowestOn(List.of("IMT", "BKR")).station()).isEqualTo("IMT");
        assertThat(Clearance.narrowestOn(List.of("BKR", "IMT")).station()).isEqualTo("BKR");
    }

    @Test
    void aRouteWithNothingNarrowReadsOpenWide() {
        assertThat(Clearance.narrowestOn(List.of("ESC", "PWR")).side()).isEqualTo(Length.of(13, 6));
        assertThatThrownBy(() -> Clearance.narrowestOn(List.of())).isInstanceOf(ClearanceError.class);
    }

    @Test
    void aPlaceNamesEachWayALoadDoesNotFit() {
        Clearance bark = Clearance.clearanceAt("BKR");
        assertThat(bark.shortfalls(ORDINARY)).isEmpty();
        assertThat(bark.shortfalls(TALL)).containsExactly("BKR clears 17'4\" and the load is 18'");
        assertThat(bark.shortfalls(HUGE))
                .containsExactly(
                        "BKR clears 17'4\" and the load is 21'6\"", "BKR clears 12'6\" wide and the load is 13'2\"");
        assertThat(bark.clears(TALL)).isFalse();
    }

    @Test
    void aRouteIsReadPlaceByPlaceInOrder() {
        assertThat(Clearance.along(List.of("PWR", "BKR")))
                .extracting(Clearance::station)
                .containsExactly("PWR", "BKR");
        assertThat(Clearance.checkRoute(HUGE, List.of("WAL", "BKR")))
                .hasSize(4)
                .first()
                .asString()
                .startsWith("WAL");
    }

    @Test
    void aLoadOnADeckIsMeasuredFromTheRail() {
        Dimensions found = Dimensions.onDeck(Length.of(4, 2), Length.of(10, 0), Length.of(10, 6));
        assertThat(found.height()).isEqualTo(Length.of(14, 2));
        assertThat(found.width()).isEqualTo(Length.of(10, 6));
        assertThat(found.smallestPlate()).contains(Plate.B);
        assertThat(Dimensions.onDeck(Length.of(4, 2), Length.of(12, 10), Length.of(10, 6))
                        .smallestPlate())
                .contains(Plate.F);
    }

    @Test
    void aLoadOnADeckStandsSomeHeightAboveIt() {
        assertThatThrownBy(() -> Dimensions.onDeck(Length.of(4, 2), Length.ZERO, Length.of(10, 6)))
                .isInstanceOf(ClearanceError.class);
    }

    @Test
    void aLoadFitsWithinBothEdges() {
        assertThat(ORDINARY.within(Length.of(15, 4), Length.of(10, 6))).isTrue();
        assertThat(ORDINARY.within(Length.of(15, 3), Length.of(10, 6))).isFalse();
        assertThat(ORDINARY.within(Length.of(15, 4), Length.of(10, 5))).isFalse();
    }

    @Test
    void aLoadInsideTheMarginIsCloseAndOneOutsideItIsNot() {
        Clearance bark = Clearance.clearanceAt("BKR");
        assertThat(bark.close(new Dimensions(Length.of(17, 2), Length.of(10, 6))))
                .isTrue();
        assertThat(bark.close(new Dimensions(Length.of(17, 1), Length.of(10, 6))))
                .isFalse();
        assertThat(bark.close(new Dimensions(Length.of(15, 0), Length.of(12, 4))))
                .isTrue();
        assertThat(bark.close(new Dimensions(Length.of(17, 5), Length.of(10, 6))))
                .isFalse();
    }

    @Test
    void aCloseLoadRunsAtRestrictedSpeedWhereItIsClose() {
        Dimensions found = new Dimensions(Length.of(17, 2), Length.of(11, 0));
        List<String> route = List.of("ESC", "BKR", "PWR", "WAL");
        assertThat(Clearance.clearsRoute(found, route)).isTrue();
        assertThat(Clearance.slowOrders(found, route))
                .containsExactly(
                        "BKR (highway overpass) 17'4\" over 12'6\" wide, restricted speed",
                        "WAL (highway bridge) 21' over 11'2\" wide, restricted speed");
        assertThat(Clearance.slowOrders(ORDINARY, route)).isEmpty();
    }
}
