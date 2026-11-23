package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.clock.Filings;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The hourly labor rate every road in the pool charges, each from the date it filed. */
public final class LaborRates {

    private final Map<ReportingMark, Filings<LaborRate>> rates = new HashMap<>();

    /** File a road's rate, refusing a second filing by the same road on the same day. */
    public LaborRate file(LaborRate rate) {
        return rates.computeIfAbsent(rate.road(), road -> new Filings<>(LaborRate::effective))
                .file(
                        rate,
                        () -> new BillingError(
                                "that road already filed a rate that day",
                                Details.of("road", rate.road().toString())));
    }

    /** The rate a road charged on a day: the latest one it had filed by then. */
    public LaborRate rateFor(ReportingMark road, LocalDate on) {
        return Optional.ofNullable(rates.get(road))
                .flatMap(filed -> filed.inForceOn(on))
                .orElseThrow(() -> new BillingError(
                        "no labor rate on file", Details.of("road", road.toString(), "on", on.toString())));
    }

    /** Every road that has filed a rate, in mark order. */
    public List<ReportingMark> roads() {
        return rates.keySet().stream().sorted().toList();
    }
}
