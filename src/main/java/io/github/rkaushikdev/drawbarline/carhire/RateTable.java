package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every car hire band the road has on file, indexed by car type so a lookup is not a scan.
 *
 * <p>Rates change on a date and never in the middle of a car cycle, which is the part that catches
 * people. A car received in December and delivered in January is billed at December rates for the
 * December hours and at January rates for the January ones, so a lookup takes a date and {@link
 * #changesBetween} says where a span has to be cut.
 */
public final class RateTable {

    private static final Comparator<Band> FILED_ORDER =
            Comparator.comparing(Band::effective).thenComparingInt(Band::fromAge);

    private final Map<CarTypeCode, List<Band>> bands = new HashMap<>();

    /** File a band, refusing one whose ages overlap a band filed for the same type and date. */
    public Band add(Band band) {
        List<Band> standing = bands.computeIfAbsent(band.typeCode(), code -> new ArrayList<>());
        for (Band other : standing) {
            if (other.effective().equals(band.effective()) && other.ages().overlaps(band.ages())) {
                throw new RateError(
                        "that band overlaps one already filed",
                        Details.of(
                                "type_code",
                                band.typeCode().toString(),
                                "effective",
                                band.effective().toString()));
            }
        }
        standing.add(band);
        standing.sort(FILED_ORDER);
        return band;
    }

    /** The rate in effect for a car of that type and age on that day. */
    public Rate lookup(CarTypeCode typeCode, int age, LocalDate on) {
        if (age < 0) {
            throw new RateError("a car has no negative age", Details.of("age", age));
        }
        if (bands.getOrDefault(typeCode, List.of()).isEmpty()) {
            throw new RateError("no rate on file for that car type", Details.of("type_code", typeCode.toString()));
        }
        return bandFor(typeCode, age, on)
                .map(Band::rate)
                .orElseThrow(() -> new RateError(
                        "no rate in effect",
                        Details.of("type_code", typeCode.toString(), "age", age, "on", on.toString())));
    }

    /** The band a car of that type and age is priced on that day: the latest one covering its age. */
    public Optional<Band> bandFor(CarTypeCode typeCode, int age, LocalDate on) {
        return bands.getOrDefault(typeCode, List.of()).stream()
                .filter(band -> !band.effective().isAfter(on) && band.coversAge(age))
                .max(Comparator.comparing(Band::effective));
    }

    /**
     * The ages of a car type the schedule has no rate for on a day, through the last year a car may
     * stay in interchange service, each run of missing ages once. A schedule that stops at forty
     * leaves every car from forty one to forty nine priced at nothing the first time one of them
     * is received, and that is found when the close refuses rather than when the sheet was filed.
     */
    public List<AgeRange> gaps(CarTypeCode typeCode, LocalDate on) {
        List<AgeRange> found = new ArrayList<>();
        int missingFrom = -1;
        for (int age = 0; age < Car.INTERCHANGE_LIFE_YEARS; age++) {
            boolean priced = bandFor(typeCode, age, on).isPresent();
            if (!priced && missingFrom < 0) {
                missingFrom = age;
            } else if (priced && missingFrom >= 0) {
                found.add(new AgeRange(missingFrom, age - 1));
                missingFrom = -1;
            }
        }
        if (missingFrom >= 0) {
            found.add(new AgeRange(missingFrom, Car.INTERCHANGE_LIFE_YEARS - 1));
        }
        return List.copyOf(found);
    }

    /** Every date the rate for a car type changes, in order. */
    public List<LocalDate> effectiveDates(CarTypeCode typeCode) {
        return bands.getOrDefault(typeCode, List.of()).stream()
                .map(Band::effective)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The dates inside a span where the rate steps, which is where a bill splits: after the first
     * day and up to and including the last.
     */
    public List<LocalDate> changesBetween(CarTypeCode typeCode, LocalDate first, LocalDate last) {
        if (last.isBefore(first)) {
            throw new RateError(
                    "that span runs backwards", Details.of("first", first.toString(), "last", last.toString()));
        }
        return effectiveDates(typeCode).stream()
                .filter(day -> day.isAfter(first) && !day.isAfter(last))
                .toList();
    }

    /** The car types the table covers, in code order. */
    public List<CarTypeCode> types() {
        return bands.keySet().stream()
                .sorted(Comparator.comparing(CarTypeCode::toString))
                .toList();
    }

    /** How many bands are on file. */
    public int size() {
        return bands.values().stream().mapToInt(List::size).sum();
    }
}
