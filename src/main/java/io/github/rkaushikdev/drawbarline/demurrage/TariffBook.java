package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.clock.Filings;
import java.time.LocalDate;
import java.util.List;

/** Every demurrage tariff the road has published, in the order they take effect. */
public final class TariffBook {

    private final Filings<Tariff> tariffs = new Filings<>(Tariff::effective);

    /** Publish a tariff, refusing a second one taking effect the same day. */
    public Tariff publish(Tariff tariff) {
        return tariffs.file(
                tariff,
                () -> new TariffError(
                        "a tariff already takes effect that day",
                        Details.of("effective", tariff.effective().toString())));
    }

    /** The tariff in force on a day: the latest one that had taken effect by then. */
    public Tariff inEffectOn(LocalDate day) {
        return tariffs.inForceOn(day)
                .orElseThrow(() -> new TariffError("no tariff was in effect", Details.of("on", day.toString())));
    }

    /** Every date a tariff takes effect, in order. */
    public List<LocalDate> effectiveDates() {
        return tariffs.effectiveDates();
    }

    /** How many tariffs have been published. */
    public int size() {
        return tariffs.size();
    }
}
