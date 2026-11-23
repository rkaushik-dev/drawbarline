package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Every filing an owner has made to take less than the published schedule.
 *
 * <p>An owner discounts for the reason anybody discounts anything: a car nobody wants at the
 * schedule rate is a car standing on their own rail earning nothing at all. A filing can only go
 * down; one that raised the rate would be a rate rise with extra steps, and the pool does not allow
 * it.
 */
public final class Elections {

    private static final Comparator<Election> FILED_ORDER =
            Comparator.comparing(Election::effective).thenComparingInt(Election::fromAge);

    private final List<Election> filed = new ArrayList<>();

    /**
     * Take a filing, refusing one that asks for more than the schedule at any age it covers. An
     * election spanning two bands of the schedule is held to the lower of them, because the older
     * cars in it would otherwise earn more than the schedule pays them.
     */
    public Election file(Election election, RateTable schedule) {
        for (int age = election.fromAge(); age <= election.toAge(); age++) {
            Rate published = schedule.lookup(election.typeCode(), age, election.effective());
            if (election.hourlyTenths() > published.hourlyTenths()) {
                throw new RateError(
                        "an election takes less than the schedule, never more",
                        Details.of(
                                "type_code", election.typeCode().toString(),
                                "age", age,
                                "elected", election.hourlyTenths(),
                                "published", published.hourlyTenths()));
            }
        }
        filed.add(election);
        filed.sort(FILED_ORDER);
        return election;
    }

    /** The elected hourly rate in force, or nothing where none was filed. */
    public Optional<Long> elected(CarTypeCode typeCode, int age, LocalDate on) {
        return filed.stream()
                .filter(one ->
                        one.typeCode().equals(typeCode) && !one.effective().isAfter(on) && one.coversAge(age))
                .max(Comparator.comparing(Election::effective))
                .map(Election::hourlyTenths);
    }

    /** Every filing, in the order they take effect. */
    public List<Election> filings() {
        return List.copyOf(filed);
    }

    public int size() {
        return filed.size();
    }

    public boolean isEmpty() {
        return filed.isEmpty();
    }
}
