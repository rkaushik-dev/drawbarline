package io.github.rkaushikdev.drawbarline.embargo;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every embargo the road has issued, live or lifted, and every permit to move through one. Cars
 * caught by one do not move, and the hours they stand are reclaimable, which is why the register is
 * kept rather than a note on somebody's desk.
 *
 * <p>A permit is granted only against an embargo that allows them, and only for days the embargo
 * is in force: a permit dated outside it is a permit for nothing, and it is usually the sign that
 * somebody wrote the wrong embargo number on it.
 */
public final class EmbargoRegister {

    private final Map<Integer, Embargo> embargoes = new LinkedHashMap<>();
    private final Map<Integer, Permit> permits = new LinkedHashMap<>();

    public Embargo issue(Embargo embargo) {
        if (embargoes.containsKey(embargo.number())) {
            throw new EmbargoError("that embargo is already issued", Details.of("number", embargo.number()));
        }
        embargoes.put(embargo.number(), embargo);
        return embargo;
    }

    /** Close an open embargo early, which is what lifting one is. */
    public Embargo lift(int number, LocalDate on) {
        Embargo standing = get(number);
        if (standing.expires().isPresent()) {
            throw new EmbargoError("that embargo already has an end", Details.of("number", number));
        }
        if (on.isBefore(standing.effective())) {
            throw new EmbargoError("an embargo cannot be lifted before it took effect", Details.of("number", number));
        }
        Embargo lifted = standing.endingOn(on);
        embargoes.put(number, lifted);
        return lifted;
    }

    public Embargo get(int number) {
        Embargo standing = embargoes.get(number);
        if (standing == null) {
            throw new EmbargoError("no such embargo", Details.of("number", number));
        }
        return standing;
    }

    /**
     * Grant a permit, refusing a number already used, an embargo that allows none, and a permit
     * running on a day the embargo is not in force.
     */
    public Permit grant(Permit permit) {
        if (permits.containsKey(permit.number())) {
            throw new EmbargoError("that permit is already granted", Details.of("number", permit.number()));
        }
        Embargo against = get(permit.embargo());
        if (!against.permitsAllowed()) {
            throw new EmbargoError("that embargo allows no permits", Details.of("embargo", against.number()));
        }
        if (!against.inForceOn(permit.issued()) || !against.inForceOn(permit.validThrough())) {
            throw new EmbargoError(
                    "a permit runs only while its embargo does",
                    Details.of("permit", permit.number(), "embargo", against.number()));
        }
        permits.put(permit.number(), permit);
        return permit;
    }

    /** The permits that let a car through on a day, in number order. */
    public List<Permit> permitsFor(CarMark car, LocalDate day) {
        return permits.values().stream()
                .filter(one -> one.covers(car, day))
                .sorted(Comparator.comparingInt(Permit::number))
                .toList();
    }

    /** The embargoes live on a day, in number order. */
    public List<Embargo> inForceOn(LocalDate day) {
        return embargoes.values().stream()
                .filter(one -> one.inForceOn(day))
                .sorted(Comparator.comparingInt(Embargo::number))
                .toList();
    }

    /** Every live embargo a particular movement runs into, in number order. */
    public List<Embargo> against(LocalDate day, Movement movement) {
        return inForceOn(day).stream().filter(one -> one.catches(movement)).toList();
    }

    /** Every live embargo one car's movement runs into once its permits are allowed for, in number order. */
    public List<Embargo> against(LocalDate day, Movement movement, CarMark car) {
        List<Integer> permitted =
                permitsFor(car, day).stream().map(Permit::embargo).toList();
        return against(day, movement).stream()
                .filter(one -> !permitted.contains(one.number()))
                .toList();
    }

    public int size() {
        return embargoes.size();
    }
}
