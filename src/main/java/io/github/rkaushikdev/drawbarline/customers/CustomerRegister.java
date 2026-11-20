package io.github.rkaushikdev.drawbarline.customers;

import io.github.rkaushikdev.drawbarline.Details;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every shipper and every piece of industry track on the line: who ships, where their track is,
 * and how often we get to it.
 */
public final class CustomerRegister implements Iterable<Industry> {

    private final Map<String, Shipper> shippers = new HashMap<>();
    private final Map<String, Industry> industries = new TreeMap<>();

    public Shipper addShipper(Shipper shipper) {
        if (shippers.containsKey(shipper.code())) {
            throw new CustomerError("that shipper is already on the register", Details.of("code", shipper.code()));
        }
        shippers.put(shipper.code(), shipper);
        return shipper;
    }

    /** File a track. Its shipper has to be on the register first. */
    public Industry addIndustry(Industry industry) {
        if (industries.containsKey(industry.code())) {
            throw new CustomerError("that industry is already on the register", Details.of("code", industry.code()));
        }
        if (!shippers.containsKey(industry.shipper().code())) {
            throw new CustomerError(
                    "the shipper has to be on the register first",
                    Details.of(
                            "industry",
                            industry.code(),
                            "shipper",
                            industry.shipper().code()));
        }
        industries.put(industry.code(), industry);
        return industry;
    }

    public Shipper shipper(String code) {
        Shipper found = shippers.get(code);
        if (found == null) {
            throw new CustomerError("no such shipper", Details.of("code", code));
        }
        return found;
    }

    public Industry industry(String code) {
        Industry found = industries.get(code);
        if (found == null) {
            throw new CustomerError("no such industry", Details.of("code", code));
        }
        return found;
    }

    /** The tracks at one station, in code order. */
    public List<Industry> atStation(String station) {
        return industries.values().stream()
                .filter(track -> track.station().equals(station))
                .toList();
    }

    /** One shipper's tracks, in code order. The shipper has to be on the register. */
    public List<Industry> forShipper(String code) {
        shipper(code);
        return industries.values().stream()
                .filter(track -> track.shipper().code().equals(code))
                .toList();
    }

    /** The shippers on an average agreement, in code order. */
    public List<Shipper> onAverageAgreement() {
        return shippers.values().stream()
                .filter(Shipper::averageAgreement)
                .sorted(Comparator.comparing(Shipper::code))
                .toList();
    }

    /** The tracks in code order. */
    @Override
    public Iterator<Industry> iterator() {
        return List.copyOf(industries.values()).iterator();
    }

    /** How many tracks are on the register. */
    public int size() {
        return industries.size();
    }
}
