package io.github.rkaushikdev.drawbarline.clock;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A run of filings of one thing, each in force from the day it takes effect until the next one
 * does: a tariff, a road's labor rate, the price of a job.
 *
 * <p>Every schedule the office keeps is read the same way, the latest filing that had taken effect
 * by the day asked about, and every one of them refuses two filings taking effect the same day,
 * because then nobody could say which was meant. The refusal is the schedule's own, so a clerk reads
 * a refusal about the tariff they filed rather than about a list.
 *
 * @param <T> what is filed
 */
public final class Filings<T> {

    private final Function<T, LocalDate> effective;
    private final List<T> filed = new ArrayList<>();

    /** An empty run of filings, each dated by {@code effective}. */
    public Filings(Function<T, LocalDate> effective) {
        this.effective = Objects.requireNonNull(effective, "effective");
    }

    /** File one, refusing with {@code sameDay} when another already takes effect that day. */
    public T file(T one, Supplier<? extends DrawbarlineError> sameDay) {
        LocalDate from = effective.apply(one);
        if (filed.stream().anyMatch(standing -> effective.apply(standing).equals(from))) {
            throw sameDay.get();
        }
        filed.add(one);
        filed.sort(Comparator.comparing(effective));
        return one;
    }

    /** The filing in force on a day: the latest that had taken effect by then. */
    public Optional<T> inForceOn(LocalDate day) {
        T found = null;
        for (T one : filed) {
            if (!effective.apply(one).isAfter(day)) {
                found = one;
            }
        }
        return Optional.ofNullable(found);
    }

    /** Every filing, in the order they take effect. */
    public List<T> all() {
        return List.copyOf(filed);
    }

    /** Every day a filing takes effect, in order. */
    public List<LocalDate> effectiveDates() {
        return filed.stream().map(effective).toList();
    }

    public int size() {
        return filed.size();
    }
}
