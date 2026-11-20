package io.github.rkaushikdev.drawbarline.network;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the road knows about where it goes and who it meets.
 *
 * <p>A road is a railroad named by its reporting mark, a station is a place on our line with the
 * time zone its clock keeps, and a junction is a station where a foreign road hands us cars or
 * takes them.
 */
public final class Network {

    private final Road home;
    private final Map<String, Road> roads = new LinkedHashMap<>();
    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Map<String, Junction> junctions = new LinkedHashMap<>();

    public Network(Road home) {
        if (!home.home()) {
            throw new ConfigurationError(
                    "the home road has to say it is home",
                    Details.of("mark", home.mark().toString()));
        }
        this.home = home;
        roads.put(home.mark().toString(), home);
    }

    /** The road this register belongs to. */
    public Road home() {
        return home;
    }

    public Road addRoad(Road road) {
        String key = road.mark().toString();
        if (roads.containsKey(key)) {
            throw new NetworkError("that road is already on the register", Details.of("mark", key));
        }
        roads.put(key, road);
        return road;
    }

    public Station addStation(Station station) {
        if (stations.containsKey(station.code())) {
            throw new NetworkError("that station is already on the register", Details.of("code", station.code()));
        }
        stations.put(station.code(), station);
        return station;
    }

    public Junction openJunction(String stationCode, String roadMark) {
        Junction junction = new Junction(station(stationCode), road(roadMark));
        if (junctions.containsKey(junction.code())) {
            throw new NetworkError("that junction is already open", Details.of("code", junction.code()));
        }
        junctions.put(junction.code(), junction);
        return junction;
    }

    public Road road(String mark) {
        Road found = roads.get(mark);
        if (found == null) {
            throw new NetworkError("no such road", Details.of("mark", mark));
        }
        return found;
    }

    public Station station(String code) {
        Station found = stations.get(code);
        if (found == null) {
            throw new NetworkError("no such station", Details.of("code", code));
        }
        return found;
    }

    public Junction junction(String code) {
        Junction found = junctions.get(code);
        if (found == null) {
            throw new NetworkError("no such junction", Details.of("code", code));
        }
        return found;
    }

    /** The junctions with one road, in station order. */
    public List<Junction> junctionsWith(String roadMark) {
        return junctions.values().stream()
                .filter(junction -> junction.road().mark().toString().equals(roadMark))
                .sorted(Comparator.comparing(junction -> junction.station().code()))
                .toList();
    }

    /** Every road, ours included, in mark order. */
    public List<Road> roads() {
        return roads.values().stream()
                .sorted(Comparator.comparing(road -> road.mark().toString()))
                .toList();
    }

    /** Every station, in code order. */
    public List<Station> stations() {
        return stations.values().stream()
                .sorted(Comparator.comparing(Station::code))
                .toList();
    }

    /** Every open junction, in code order. */
    public List<Junction> junctions() {
        return junctions.values().stream()
                .sorted(Comparator.comparing(Junction::code))
                .toList();
    }

    /** Every road but our own, in mark order. */
    public List<Road> foreignRoads() {
        return roads().stream().filter(road -> !road.home()).toList();
    }
}
