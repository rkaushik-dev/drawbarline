package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Every demurrage tariff the road has published, in the order they take effect. */
public final class TariffBook {

    private final List<Tariff> tariffs = new ArrayList<>();

    /** Publish a tariff, refusing a second one taking effect the same day. */
    public Tariff publish(Tariff tariff) {
        if (tariffs.stream().anyMatch(one -> one.effective().equals(tariff.effective()))) {
            throw new TariffError(
                    "a tariff already takes effect that day",
                    Details.of("effective", tariff.effective().toString()));
        }
        tariffs.add(tariff);
        tariffs.sort(Comparator.comparing(Tariff::effective));
        return tariff;
    }

    /** The tariff in force on a day: the latest one that had taken effect by then. */
    public Tariff inEffectOn(LocalDate day) {
        Tariff found = null;
        for (Tariff one : tariffs) {
            if (!one.effective().isAfter(day)) {
                found = one;
            }
        }
        if (found == null) {
            throw new TariffError("no tariff was in effect", Details.of("on", day.toString()));
        }
        return found;
    }

    /** Every date a tariff takes effect, in order. */
    public List<LocalDate> effectiveDates() {
        return tariffs.stream().map(Tariff::effective).toList();
    }

    /** How many tariffs have been published. */
    public int size() {
        return tariffs.size();
    }
}
