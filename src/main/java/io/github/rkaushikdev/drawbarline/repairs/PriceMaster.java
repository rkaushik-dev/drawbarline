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
    private final Map<String, Money> scrap = new HashMap<>();

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

    /**
     * File what one part of a family is worth as scrap when it comes off a car, replacing any
     * credit the family had. A wheelset is worth a good deal to the scrap dealer and a brake shoe
     * is worth nothing, so the credit is per family rather than per card.
     */
    public Money creditScrap(String family, Money perPart) {
        if (perPart.cents() < 0) {
            throw new BillingError("scrap is not worth less than nothing", Details.of("family", family));
        }
        scrap.put(family, perPart);
        return perPart;
    }

    /** What one part removed under a job is worth as scrap, nothing where the family carries no credit. */
    public Money scrapFor(JobCode job) {
        return scrap.getOrDefault(job.family(), Money.ZERO);
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
