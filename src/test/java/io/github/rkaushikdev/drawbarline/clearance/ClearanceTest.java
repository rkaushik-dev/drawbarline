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
}
