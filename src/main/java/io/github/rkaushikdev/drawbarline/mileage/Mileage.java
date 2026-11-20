package io.github.rkaushikdev.drawbarline.mileage;

import io.github.rkaushikdev.drawbarline.Details;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the stations sit, and how far it is between two of them.
 *
 * <p>Car hire has a mileage part as well as an hourly one, and the miles are the chargeable miles
 * the car moved on our rail. The road keeps a milepost for every station on each of its two
 * lines, and a distance is the difference between two of them.
 *
 * <p>The branch leaves the main at Powers and there is no other connection, so a move from Iron
 * Mountain to Green Bay is measured down the branch to Powers and then along the main. That is
 * not a routing algorithm, it is the shape of this particular railroad.
 */
public final class Mileage {

    public static final String MAIN = "M";
    public static final String BRANCH = "B";
    public static final String JUNCTION_STATION = "PWR";
    public static final int TENTHS_PER_MILE = 10;

    /** Every station's milepost. Escanaba is the zero of the main; the branch takes its own zero at Powers. */
    public static final Map<String, Milepost> MILEPOSTS = mileposts();

    private Mileage() {}

    private static Map<String, Milepost> mileposts() {
        Map<String, Milepost> found = new LinkedHashMap<>();
        found.put("ESC", new Milepost(MAIN, 0));
        found.put("WLS", new Milepost(MAIN, 42));
        found.put("FRD", new Milepost(MAIN, 98));
        found.put("BKR", new Milepost(MAIN, 175));
        found.put("HRM", new Milepost(MAIN, 271));
        found.put("PWR", new Milepost(MAIN, 336));
        found.put("SPL", new Milepost(MAIN, 390));
        found.put("WAL", new Milepost(MAIN, 462));
        found.put("STE", new Milepost(MAIN, 528));
        found.put("MEN", new Milepost(MAIN, 624));
        found.put("MRN", new Milepost(MAIN, 641));
        found.put("PSH", new Milepost(MAIN, 713));
        found.put("OCO", new Milepost(MAIN, 847));
        found.put("GRB", new Milepost(MAIN, 1089));
        found.put("IMT", new Milepost(BRANCH, 286));
        found.put("NIA", new Milepost(BRANCH, 412));
        return Collections.unmodifiableMap(found);
    }

    /** The milepost of a station on our line. */
    public static Milepost milepost(String stationCode) {
        Milepost found = MILEPOSTS.get(stationCode);
        if (found == null) {
            throw new MileageError("no milepost for that station", Details.of("station", stationCode));
        }
        return found;
    }

    /** Track distance in tenths of a mile, by way of Powers when it has to be. */
    public static int tenthsBetween(String first, String second) {
        Milepost here = milepost(first);
        Milepost there = milepost(second);
        if (here.line().equals(there.line())) {
            return Math.abs(here.tenths() - there.tenths());
        }
        Milepost junction = milepost(JUNCTION_STATION);
        Milepost onBranch = here.line().equals(BRANCH) ? here : there;
        Milepost onMain = here.line().equals(BRANCH) ? there : here;
        return onBranch.tenths() + Math.abs(onMain.tenths() - junction.tenths());
    }

    /** Track distance in decimal miles, for a report. */
    public static double milesBetween(String first, String second) {
        return tenthsBetween(first, second) / (double) TENTHS_PER_MILE;
    }

    /** The distance over a sequence of stations, which is what a movement records. */
    public static int routeTenths(List<String> stations) {
        if (stations.size() < 2) {
            throw new MileageError("a route needs at least two places", Details.of("stations", List.copyOf(stations)));
        }
        int total = 0;
        for (int at = 1; at < stations.size(); at++) {
            total += tenthsBetween(stations.get(at - 1), stations.get(at));
        }
        return total;
    }

    /** Every station on one line, in milepost order. */
    public static List<String> stationsOn(String line) {
        if (!MAIN.equals(line) && !BRANCH.equals(line)) {
            throw new MileageError("this road has a main and a branch", Details.of("line", line));
        }
        return MILEPOSTS.entrySet().stream()
                .filter(entry -> entry.getValue().line().equals(line))
                .sorted(Comparator.comparingInt(entry -> entry.getValue().tenths()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
