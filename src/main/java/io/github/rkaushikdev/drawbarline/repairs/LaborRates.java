package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The hourly labor rate every road in the pool charges, each from the date it filed. */
public final class LaborRates {

    private final Map<ReportingMark, List<LaborRate>> rates = new HashMap<>();

    /** File a road's rate, refusing a second filing by the same road on the same day. */
    public LaborRate file(LaborRate rate) {
        List<LaborRate> standing = rates.computeIfAbsent(rate.road(), road -> new ArrayList<>());
        if (standing.stream().anyMatch(one -> one.effective().equals(rate.effective()))) {
            throw new BillingError(
                    "that road already filed a rate that day",
                    Details.of("road", rate.road().toString()));
        }
        standing.add(rate);
        standing.sort(Comparator.comparing(LaborRate::effective));
        return rate;
    }

    /** The rate a road charged on a day: the latest one it had filed by then. */
    public LaborRate rateFor(ReportingMark road, LocalDate on) {
        LaborRate found = null;
        for (LaborRate one : rates.getOrDefault(road, List.of())) {
            if (!one.effective().isAfter(on)) {
                found = one;
            }
        }
        if (found == null) {
            throw new BillingError("no labor rate on file", Details.of("road", road.toString(), "on", on.toString()));
        }
        return found;
    }

    /** Every road that has filed a rate, in mark order. */
    public List<ReportingMark> roads() {
        return rates.keySet().stream().sorted().toList();
    }
}
