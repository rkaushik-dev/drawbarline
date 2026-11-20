package io.github.rkaushikdev.drawbarline.mileage;

import static io.github.rkaushikdev.drawbarline.mileage.Mileage.BRANCH;
import static io.github.rkaushikdev.drawbarline.mileage.Mileage.MAIN;
import static io.github.rkaushikdev.drawbarline.mileage.Mileage.MILEPOSTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.network.Station;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MileageTest {

    @Test
    void everyStationOnTheLineHasAMilepost() {
        assertThat(Reference.STATIONS.stream().map(Station::code).collect(Collectors.toSet()))
                .isEqualTo(MILEPOSTS.keySet());
    }

    @Test
    void aMilepostPrintsItsLineAndTenths() {
        assertThat(new Milepost(MAIN, 336)).hasToString("M33.6");
    }

    @Test
    void aMilepostOnAWholeMileStillPrintsATenth() {
        assertThat(new Milepost(BRANCH, 280)).hasToString("B28.0");
    }

    @Test
    void aMilepostReadsAsDecimalMiles() {
        assertThat(new Milepost(MAIN, 175).miles()).isEqualTo(17.5);
    }

    @Test
    void aLineThisRoadDoesNotHaveIsRefused() {
        assertThatThrownBy(() -> new Milepost("C", 100)).isInstanceOf(MileageError.class);
    }

    @Test
    void aMilepostBelowZeroIsRefused() {
        assertThatThrownBy(() -> new Milepost(MAIN, -1)).isInstanceOf(MileageError.class);
    }

    @Test
    void escanabaIsTheZeroOfTheMain() {
        assertThat(Mileage.milepost("ESC").tenths()).isZero();
    }

    @Test
    void theBranchTakesItsOwnZeroAtPowers() {
        assertThat(Mileage.milepost("PWR").line()).isEqualTo(MAIN);
        assertThat(Mileage.milepost("IMT").line()).isEqualTo(BRANCH);
    }

    @Test
    void aStationOffTheRailroadHasNoMilepost() {
        assertThatThrownBy(() -> Mileage.milepost("CHI")).isInstanceOf(MileageError.class);
    }

    @Test
    void twoStationsOnTheMainMeasureByDifference() {
        assertThat(Mileage.tenthsBetween("ESC", "GRB")).isEqualTo(1089);
    }

    @Test
    void distanceDoesNotCareWhichWayRound() {
        assertThat(Mileage.tenthsBetween("GRB", "ESC")).isEqualTo(Mileage.tenthsBetween("ESC", "GRB"));
    }

    @Test
    void aStationToItselfIsNoDistance() {
        assertThat(Mileage.tenthsBetween("PWR", "PWR")).isZero();
    }

    @Test
    void aMoveOffTheBranchGoesByWayOfPowers() {
        assertThat(Mileage.tenthsBetween("IMT", "ESC")).isEqualTo(286 + 336);
    }

    @Test
    void aMoveOntoTheBranchMeasuresTheSameWay() {
        assertThat(Mileage.tenthsBetween("GRB", "NIA")).isEqualTo(412 + (1089 - 336));
    }

    @Test
    void twoStationsOnTheBranchMeasureByDifference() {
        assertThat(Mileage.tenthsBetween("IMT", "NIA")).isEqualTo(126);
    }

    @Test
    void powersToABranchStationIsJustTheBranch() {
        assertThat(Mileage.tenthsBetween("PWR", "IMT")).isEqualTo(286);
    }

    @Test
    void milesReadAsDecimalsForAReport() {
        assertThat(Mileage.milesBetween("ESC", "GRB")).isEqualTo(108.9);
    }

    @Test
    void aRouteAddsUpItsLegs() {
        assertThat(Mileage.routeTenths(List.of("ESC", "PWR", "IMT"))).isEqualTo(336 + 286);
    }

    @Test
    void aRouteThatDoublesBackCountsBothWays() {
        assertThat(Mileage.routeTenths(List.of("ESC", "PWR", "ESC"))).isEqualTo(672);
    }

    @Test
    void aRouteOfOnePlaceIsRefused() {
        assertThatThrownBy(() -> Mileage.routeTenths(List.of("ESC"))).isInstanceOf(MileageError.class);
    }

    @Test
    void aRouteThroughAStationWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> Mileage.routeTenths(List.of("ESC", "CHI"))).isInstanceOf(MileageError.class);
    }

    @Test
    void theMainRunsFromEscanabaToGreenBay() {
        List<String> main = Mileage.stationsOn(MAIN);
        assertThat(main.get(0)).isEqualTo("ESC");
        assertThat(main.get(main.size() - 1)).isEqualTo("GRB");
    }

    @Test
    void theBranchHoldsTwoStations() {
        assertThat(Mileage.stationsOn(BRANCH)).containsExactly("IMT", "NIA");
    }

    @Test
    void aLineThisRoadDoesNotHaveListsNothing() {
        assertThatThrownBy(() -> Mileage.stationsOn("C")).isInstanceOf(MileageError.class);
    }

    @Test
    void theStationsComeBackInMilepostOrder() {
        List<Integer> posts = Mileage.stationsOn(MAIN).stream()
                .map(code -> MILEPOSTS.get(code).tenths())
                .toList();
        assertThat(posts).isSorted();
    }
}
