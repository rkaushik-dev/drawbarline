package io.github.rkaushikdev.drawbarline.reference;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.network.Road;
import io.github.rkaushikdev.drawbarline.network.Station;
import java.util.List;

/**
 * The Drawbar Line itself, as data.
 *
 * <p>Sixteen stations from the ore dock at Escanaba down the west shore of Green Bay into
 * Wisconsin, and a branch off Powers up to Iron Mountain and the state line at Niagara. Two roads
 * and a short line connect with us, at seven places.
 *
 * <p>None of this is inferred. It changes when the road opens or closes a junction, which is a
 * filing, not a deployment, so it lives in one method that builds the register.
 */
public final class Reference {

    public static final String EASTERN = "America/Detroit";
    public static final String CENTRAL = "America/Chicago";

    /** Our own road. */
    public static final Road DRAWBAR_LINE = new Road(new ReportingMark("DBL"), "Drawbar Line Railway", true);

    /** The roads that meet us. */
    public static final List<Road> CONNECTIONS = List.of(
            new Road(new ReportingMark("CN"), "Canadian National"),
            new Road(new ReportingMark("ELS"), "Escanaba and Lake Superior"),
            new Road(new ReportingMark("CP"), "Canadian Pacific"));

    /**
     * Every station, from the ore dock outwards. Delta county keeps eastern time and Menominee
     * county keeps central, so the boundary falls between Bark River and Hermansville; every hour
     * billed across it depends on getting that right.
     */
    public static final List<Station> STATIONS = List.of(
            new Station("ESC", "Escanaba", "MI", EASTERN),
            new Station("WLS", "Wells", "MI", EASTERN),
            new Station("FRD", "Ford River", "MI", EASTERN),
            new Station("BKR", "Bark River", "MI", EASTERN),
            new Station("HRM", "Hermansville", "MI", CENTRAL),
            new Station("PWR", "Powers", "MI", CENTRAL),
            new Station("SPL", "Spalding", "MI", CENTRAL),
            new Station("WAL", "Wallace", "MI", CENTRAL),
            new Station("STE", "Stephenson", "MI", CENTRAL),
            new Station("MEN", "Menominee", "MI", CENTRAL),
            new Station("MRN", "Marinette", "WI", CENTRAL),
            new Station("PSH", "Peshtigo", "WI", CENTRAL),
            new Station("OCO", "Oconto", "WI", CENTRAL),
            new Station("GRB", "Green Bay", "WI", CENTRAL),
            new Station("IMT", "Iron Mountain", "MI", CENTRAL),
            new Station("NIA", "Niagara", "WI", CENTRAL));

    /** Where each connection meets us. */
    public static final List<JunctionSite> JUNCTIONS = List.of(
            new JunctionSite("ESC", "CN"),
            new JunctionSite("ESC", "ELS"),
            new JunctionSite("PWR", "ELS"),
            new JunctionSite("MRN", "CN"),
            new JunctionSite("GRB", "CN"),
            new JunctionSite("GRB", "CP"),
            new JunctionSite("IMT", "ELS"));

    private Reference() {}

    /** Build the register the way the road actually is. Every call builds a fresh one. */
    public static Network drawbarLine() {
        Network network = new Network(DRAWBAR_LINE);
        for (Road road : CONNECTIONS) {
            network.addRoad(road);
        }
        for (Station station : STATIONS) {
            network.addStation(station);
        }
        for (JunctionSite site : JUNCTIONS) {
            network.openJunction(site.stationCode(), site.roadMark());
        }
        return network;
    }
}
