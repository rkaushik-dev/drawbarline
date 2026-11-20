package io.github.rkaushikdev.drawbarline.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import org.junit.jupiter.api.Test;

class NetworkTest {

    static final Road HOME = new Road(new ReportingMark("DBL"), "Drawbar Line Railway", true);
    static final Road CN = new Road(new ReportingMark("CN"), "Canadian National");
    static final Road WC = new Road(new ReportingMark("WC"), "Wisconsin Central");

    static final Station ESCANABA = new Station("ESC", "Escanaba", "MI", "America/Detroit");
    static final Station POWERS = new Station("PWR", "Powers", "MI", "America/Chicago");
    static final Station MARINETTE = new Station("MRN", "Marinette", "WI", "America/Chicago");

    static Network aNetwork() {
        Network net = new Network(HOME);
        net.addRoad(CN);
        net.addRoad(WC);
        for (Station station : new Station[] {ESCANABA, POWERS, MARINETTE}) {
            net.addStation(station);
        }
        return net;
    }

    @Test
    void aRoadPrintsItsMarkAndName() {
        assertThat(CN).hasToString("CN Canadian National");
    }

    @Test
    void aRoadNeedsAName() {
        assertThatThrownBy(() -> new Road(new ReportingMark("CN"), "   ")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aRoadDoesNotCarryAPrivateMark() {
        assertThatThrownBy(() -> new Road(new ReportingMark("PLMX"), "Pullman Leasing"))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aStationPrintsItsNameAndState() {
        assertThat(ESCANABA).hasToString("Escanaba, MI");
    }

    @Test
    void aStationCodeIsUpperCase() {
        assertThatThrownBy(() -> new Station("esc", "Escanaba", "MI", "America/Detroit"))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aStationOffTheLineIsRefused() {
        assertThatThrownBy(() -> new Station("CHI", "Chicago", "IL", "America/Chicago"))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aStationWithAnUnknownZoneIsRefused() {
        assertThatThrownBy(() -> new Station("ESC", "Escanaba", "MI", "America/Nowhere"))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theLineCrossesATimeZoneBoundary() {
        assertThat(ESCANABA.tz().getId()).isEqualTo("America/Detroit");
        assertThat(POWERS.tz().getId()).isEqualTo("America/Chicago");
    }

    @Test
    void aJunctionNamesTheRoadAndThePlace() {
        Junction junction = new Junction(ESCANABA, CN);
        assertThat(junction).hasToString("CN at Escanaba");
        assertThat(junction.code()).isEqualTo("ESC/CN");
    }

    @Test
    void aJunctionWithOurOwnRoadIsRefused() {
        assertThatThrownBy(() -> new Junction(ESCANABA, HOME)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theHomeRoadHasToSayItIsHome() {
        assertThatThrownBy(() -> new Network(CN)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theHomeRoadIsOnTheRegisterFromTheStart() {
        assertThat(aNetwork().road("DBL")).isSameAs(HOME);
    }

    @Test
    void aRoadAddedTwiceIsRefused() {
        Network net = aNetwork();
        assertThatThrownBy(() -> net.addRoad(CN)).isInstanceOf(NetworkError.class);
    }

    @Test
    void aStationAddedTwiceIsRefused() {
        Network net = aNetwork();
        assertThatThrownBy(() -> net.addStation(ESCANABA)).isInstanceOf(NetworkError.class);
    }

    @Test
    void anUnknownRoadIsRefused() {
        assertThatThrownBy(() -> aNetwork().road("BNSF")).isInstanceOf(NetworkError.class);
    }

    @Test
    void anUnknownStationIsRefused() {
        assertThatThrownBy(() -> aNetwork().station("ZZZ")).isInstanceOf(NetworkError.class);
    }

    @Test
    void anUnknownJunctionIsRefused() {
        assertThatThrownBy(() -> aNetwork().junction("ESC/CN")).isInstanceOf(NetworkError.class);
    }

    @Test
    void openingAJunctionPutsItOnTheRegister() {
        Network net = aNetwork();
        Junction junction = net.openJunction("ESC", "CN");
        assertThat(net.junction("ESC/CN")).isEqualTo(junction);
    }

    @Test
    void openingTheSameJunctionTwiceIsRefused() {
        Network net = aNetwork();
        net.openJunction("ESC", "CN");
        assertThatThrownBy(() -> net.openJunction("ESC", "CN")).isInstanceOf(NetworkError.class);
    }

    @Test
    void aJunctionAtAnUnknownStationIsRefused() {
        Network net = aNetwork();
        assertThatThrownBy(() -> net.openJunction("ZZZ", "CN")).isInstanceOf(NetworkError.class);
    }

    @Test
    void junctionsWithOneRoadComeBackInStationOrder() {
        Network net = aNetwork();
        net.openJunction("MRN", "CN");
        net.openJunction("ESC", "CN");
        net.openJunction("PWR", "WC");
        assertThat(net.junctionsWith("CN"))
                .extracting(junction -> junction.station().code())
                .containsExactly("ESC", "MRN");
    }

    @Test
    void theForeignRoadsLeaveOutOurOwn() {
        assertThat(aNetwork().foreignRoads())
                .extracting(road -> road.mark().toString())
                .containsExactly("CN", "WC");
    }

    @Test
    void theRegistersComeBackSorted() {
        Network net = aNetwork();
        net.openJunction("PWR", "WC");
        net.openJunction("ESC", "CN");
        assertThat(net.stations()).extracting(Station::code).containsExactly("ESC", "MRN", "PWR");
        assertThat(net.junctions()).extracting(Junction::code).containsExactly("ESC/CN", "PWR/WC");
        assertThat(net.roads()).extracting(road -> road.mark().toString()).containsExactly("CN", "DBL", "WC");
    }
}
