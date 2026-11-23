package io.github.rkaushikdev.drawbarline.storage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.clock.Filings;
import java.time.LocalDate;
import java.util.List;

/**
 * Every storage tariff the road has published, in the order they take effect.
 *
 * <p>A car goes into storage under the storage tariff in force on the day it was placed, the same
 * way it is counted under the demurrage tariff it was placed under: a customer who placed a car in
 * March under a conversion after ten days is not converted after five because a new tariff took
 * effect in April while the car stood.
 */
public final class StorageTariffs {

    private final Filings<StorageTariff> tariffs = new Filings<>(StorageTariff::effective);

    /** Publish a storage tariff, refusing a second one taking effect the same day. */
    public StorageTariff publish(StorageTariff tariff) {
        return tariffs.file(
                tariff,
                () -> new TariffError(
                        "a storage tariff already takes effect that day",
                        Details.of("effective", tariff.effective().toString())));
    }

    /** The storage tariff in force on a day. */
    public StorageTariff inEffectOn(LocalDate day) {
        return tariffs.inForceOn(day)
                .orElseThrow(
                        () -> new TariffError("no storage tariff was in effect", Details.of("on", day.toString())));
    }

    /** Every storage tariff, in the order they take effect. */
    public List<StorageTariff> all() {
        return tariffs.all();
    }
}
