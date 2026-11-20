package io.github.rkaushikdev.drawbarline.embargo;

import io.github.rkaushikdev.drawbarline.Details;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every embargo the road has issued, live or lifted. Cars caught by one do not move, and the
 * hours they stand are reclaimable, which is why the register is kept rather than a note on
 * somebody's desk.
 */
public final class EmbargoRegister {

    private final Map<Integer, Embargo> embargoes = new LinkedHashMap<>();

    public Embargo issue(Embargo embargo) {
        if (embargoes.containsKey(embargo.number())) {
            throw new EmbargoError("that embargo is already issued", Details.of("number", embargo.number()));
        }
        embargoes.put(embargo.number(), embargo);
        return embargo;
    }

    /** Close an open embargo early, which is what lifting one is. */
    public Embargo lift(int number, LocalDate on) {
        Embargo standing = embargoes.get(number);
        if (standing == null) {
            throw new EmbargoError("no such embargo", Details.of("number", number));
        }
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

    public int size() {
        return embargoes.size();
    }
}
