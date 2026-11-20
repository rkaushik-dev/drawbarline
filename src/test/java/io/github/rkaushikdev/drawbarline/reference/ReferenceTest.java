package io.github.rkaushikdev.drawbarline.reference;

import static io.github.rkaushikdev.drawbarline.reference.Reference.CENTRAL;
import static io.github.rkaushikdev.drawbarline.reference.Reference.EASTERN;
import static io.github.rkaushikdev.drawbarline.reference.Reference.JUNCTIONS;
import static io.github.rkaushikdev.drawbarline.reference.Reference.STATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.network.Junction;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.network.NetworkError;
import io.github.rkaushikdev.drawbarline.network.Station;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReferenceTest {

    static Network line;

    @BeforeAll
    static void build() {
        line = Reference.drawbarLine();
    }

    @Test
    void theHomeRoadIsTheDrawbarLine() {
        assertThat(line.home().name()).isEqualTo("Drawbar Line Railway");
        assertThat(line.home().mark()).hasToString("DBL");
    }

    @Test
    void threeRoadsConnectWithUs() {
        assertThat(line.foreignRoads())
                .extracting(road -> road.mark().toString())
                .containsExactly("CN", "CP", "ELS");
    }

    @Test
    void everyStationInTheTableIsOnTheRegister() {
        assertThat(line.stations()).hasSize(STATIONS.size());
    }

    @Test
    void stationCodesAreUnique() {
        assertThat(STATIONS).extracting(Station::code).doesNotHaveDuplicates();
    }

    @Test
    void theOreDockEndIsOnEasternTime() {
        assertThat(line.station("ESC").zone()).isEqualTo(EASTERN);
    }

    @Test
    void theWisconsinEndIsOnCentralTime() {
        assertThat(line.station("GRB").zone()).isEqualTo(CENTRAL);
    }

    @Test
    void theZoneSeamFallsBetweenBarkRiverAndHermansville() {
        assertThat(line.station("BKR").zone()).isEqualTo(EASTERN);
        assertThat(line.station("HRM").zone()).isEqualTo(CENTRAL);
    }

    @Test
    void onlyTheDeltaCountyStationsKeepEasternTime() {
        Set<String> eastern = STATIONS.stream()
                .filter(station -> station.zone().equals(EASTERN))
                .map(Station::code)
                .collect(Collectors.toSet());
        assertThat(eastern).isEqualTo(Set.of("ESC", "WLS", "FRD", "BKR"));
    }

    @Test
    void theLineRunsIntoWisconsin() {
        assertThat(STATIONS.stream().map(Station::state).collect(Collectors.toSet()))
                .isEqualTo(Set.of("MI", "WI"));
    }

    @Test
    void everyJunctionInTheTableIsOpen() {
        assertThat(line.junctions()).hasSize(JUNCTIONS.size());
    }

    @Test
    void theShortLineMeetsUsInThreePlaces() {
        assertThat(line.junctionsWith("ELS"))
                .extracting(junction -> junction.station().code())
                .containsExactly("ESC", "IMT", "PWR");
    }

    @Test
    void canadianPacificMeetsUsOnlyAtGreenBay() {
        assertThat(line.junctionsWith("CP"))
                .extracting(junction -> junction.station().code())
                .containsExactly("GRB");
    }

    @Test
    void aJunctionReadsAsTheRoadAtThePlace() {
        assertThat(line.junction("MRN/CN")).hasToString("CN at Marinette");
    }

    @Test
    void nobodyInterchangesAtWallace() {
        assertThat(line.junctionsWith("CN")).isNotEmpty();
        assertThat(line.junctions())
                .extracting(Junction::station)
                .extracting(Station::code)
                .doesNotContain("WAL");
    }

    @Test
    void aRoadWeDoNotMeetIsUnknown() {
        assertThatThrownBy(() -> line.road("UP")).isInstanceOf(NetworkError.class);
    }

    @Test
    void buildingTheLineTwiceGivesTwoIndependentRegisters() {
        Network first = Reference.drawbarLine();
        Network second = Reference.drawbarLine();
        assertThat(first).isNotSameAs(second);
        assertThat(first.stations())
                .extracting(Station::code)
                .isEqualTo(second.stations().stream().map(Station::code).toList());
    }
}
