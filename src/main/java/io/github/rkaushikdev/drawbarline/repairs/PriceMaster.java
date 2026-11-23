package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.clock.Filings;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Every priced job, and the depreciation each family of parts carries.
 *
 * <p>What a repair costs comes out of here before anybody argues about who pays it.
 */
public final class PriceMaster {

    private final Map<JobCode, Filings<PriceLine>> lines = new HashMap<>();
    private final Map<String, Depreciation> depreciation = new HashMap<>();

    /** File a price line, refusing a second price for the same job from the same date. */
    public PriceLine add(PriceLine line) {
        return lines.computeIfAbsent(line.job(), job -> new Filings<>(PriceLine::effective))
                .file(
                        line,
                        () -> new BillingError(
                                "that job is already priced from that date",
                                Details.of(
                                        "job",
                                        line.job().toString(),
                                        "effective",
                                        line.effective().toString())));
    }

    /** File the depreciation rule for a family, replacing any rule it had. */
    public Depreciation depreciateFamily(Depreciation rule) {
        depreciation.put(rule.family(), rule);
        return rule;
    }

    /** The price in force for a job on a day: the latest line priced by then. */
    public PriceLine lineFor(JobCode job, LocalDate on) {
        return Optional.ofNullable(lines.get(job))
                .flatMap(filed -> filed.inForceOn(on))
                .orElseThrow(() ->
                        new BillingError("no price on file", Details.of("job", job.toString(), "on", on.toString())));
    }

    /** The depreciation rule a job's family carries, if it carries one. */
    public Optional<Depreciation> depreciationFor(JobCode job) {
        return Optional.ofNullable(depreciation.get(job.family()));
    }

    /** The material price with any depreciation the family carries taken off. */
    public Money materialFor(JobCode job, LocalDate on, int years) {
        PriceLine line = lineFor(job, on);
        return depreciationFor(job)
                .map(rule -> rule.applied(line.material(), years))
                .orElse(line.material());
    }

    /** How many price lines are on file. */
    public int size() {
        return lines.values().stream().mapToInt(Filings::size).sum();
    }
}
