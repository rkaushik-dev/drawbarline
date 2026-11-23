package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How many cars a day each of a customer's tracks is worked at, and the bunching relief that comes
 * out of it.
 *
 * <p>A shipper with a mill house track and a woodyard does not unload pulpwood at the rate it
 * loads paper. Relief reckoned at one rate for both either gives the woodyard days it never lost
 * or takes them off the mill, so each track is reckoned at its own rate and the rest at the
 * customer's ordinary one.
 *
 * <p>On each track, the cars placed the same day are taken in car order, that track's rate to a
 * day, and every car past the first day's worth is pushed back a day for each day's worth ahead of
 * it. Relief is handed out in car mark order so two runs of the same month relieve the same cars.
 */
public final class Bunching {

    private final int ordinary;
    private final Map<String, Integer> perTrack;

    private Bunching(int ordinary, Map<String, Integer> perTrack) {
        requireAtLeastOne(ordinary, "ordinary");
        perTrack.forEach((track, rate) -> requireAtLeastOne(rate, track));
        this.ordinary = ordinary;
        this.perTrack = Map.copyOf(perTrack);
    }

    /** Every track worked at the same rate. */
    public static Bunching flat(int perDay) {
        return new Bunching(perDay, Map.of());
    }

    /** These tracks at their own rates, every other track at the ordinary one. */
    public static Bunching byTrack(int ordinary, Map<String, Integer> perTrack) {
        return new Bunching(ordinary, perTrack);
    }

    private static void requireAtLeastOne(int perDay, String track) {
        if (perDay < 1) {
            throw new TariffError(
                    "a customer works at least one car a day", Details.of("per_day", perDay, "track", track));
        }
    }

    /** The cars a day one track is worked at. */
    public int perDay(String industry) {
        return perTrack.getOrDefault(industry, ordinary);
    }

    /** How far back each car has to be pushed for the delivery to each track to read as even. */
    public List<Relief> reliefs(List<Detention> detentions) {
        Map<Delivery, List<Detention>> grouped = new LinkedHashMap<>();
        for (Detention one : detentions) {
            grouped.computeIfAbsent(new Delivery(one.industry(), one.placedOn()), key -> new ArrayList<>())
                    .add(one);
        }
        List<Relief> found = new ArrayList<>();
        grouped.forEach((delivery, group) -> {
            int rate = perDay(delivery.industry());
            List<Detention> ordered = group.stream()
                    .sorted(Comparator.comparing(one -> one.car().toString()))
                    .toList();
            for (int index = rate; index < ordered.size(); index++) {
                found.add(new Relief(ordered.get(index).car(), delivery.industry(), index / rate));
            }
        });
        found.sort(Comparator.comparing(Relief::industry)
                .thenComparing(one -> one.car().toString()));
        return List.copyOf(found);
    }

    /** Recount a set of detentions with bunching relief applied, in the order they were given. */
    public List<Detention> relieve(List<Detention> detentions, Tariff tariff) {
        Map<CarMark, Integer> allowed = new HashMap<>();
        for (Relief one : reliefs(detentions)) {
            allowed.put(one.car(), one.days());
        }
        return detentions.stream()
                .map(one -> Relief.recount(one, tariff, allowed.getOrDefault(one.car(), 0)))
                .toList();
    }

    /** The cars set out on one track on one day, which is what bunching is reckoned over. */
    private record Delivery(String industry, LocalDate placedOn) {}
}
