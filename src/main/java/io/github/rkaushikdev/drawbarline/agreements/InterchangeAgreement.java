package io.github.rkaushikdev.drawbarline.agreements;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.clock.Term;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What one road takes at one junction: which kinds of car, whether hazardous material at all, how
 * many cars a day, and the hours somebody is there to take them, in local time at the junction.
 */
public record InterchangeAgreement(
        ReportingMark road,
        String junction,
        LocalDate effective,
        Set<CarKind> kinds,
        int dailyCap,
        LocalTime opens,
        LocalTime closes,
        boolean hazardous,
        Optional<LocalDate> expires) {

    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    public InterchangeAgreement {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(opens, "opens");
        Objects.requireNonNull(closes, "closes");
        Objects.requireNonNull(expires, "expires");
        Mileage.milepost(junction);
        if (kinds.isEmpty()) {
            throw new AgreementError(
                    "an agreement that takes no kind of car is not an agreement", Details.of("junction", junction));
        }
        kinds = Collections.unmodifiableSet(EnumSet.copyOf(kinds));
        if (dailyCap < 1) {
            throw new AgreementError("a cap of nothing closes the junction", Details.of("junction", junction));
        }
        if (!opens.isBefore(closes)) {
            throw new AgreementError("the junction opens before it closes", Details.of("junction", junction));
        }
        Term.checked(
                effective,
                expires,
                () -> new AgreementError(
                        "an agreement cannot expire before it starts", Details.of("junction", junction)));
    }

    /** An open-ended agreement that takes no hazardous material. */
    public InterchangeAgreement(
            ReportingMark road,
            String junction,
            LocalDate effective,
            Set<CarKind> kinds,
            int dailyCap,
            LocalTime opens,
            LocalTime closes) {
        this(road, junction, effective, kinds, dailyCap, opens, closes, false, Optional.empty());
    }

    /** The junction and the road: {@code ESC/CN}. */
    public String code() {
        return junction + "/" + road;
    }

    /** The days the agreement is in force. */
    public Term term() {
        return new Term(effective, expires);
    }

    public boolean inForceOn(LocalDate day) {
        return term().covers(day);
    }

    /** Whether somebody is at the junction at a local clock reading; the closing minute is closed. */
    public boolean mannedAt(LocalDateTime local) {
        LocalTime at = local.toLocalTime();
        return !at.isBefore(opens) && at.isBefore(closes);
    }

    public boolean takesKind(CarKind kind) {
        return kinds.contains(kind);
    }

    /**
     * Every reason the connection will not take a car offered at a local clock reading: no
     * agreement in force that day, a kind it does not take, hazardous material it does not take,
     * the junction shut, or the day's cap already used, in that order.
     *
     * @param alreadyToday the cars already gone across today
     */
    public List<String> refusals(CarKind kind, LocalDateTime local, boolean hazardous, int alreadyToday) {
        requireACount(alreadyToday);
        List<String> found = new ArrayList<>();
        if (!inForceOn(local.toLocalDate())) {
            found.add(code() + " has no agreement in force");
        }
        if (!takesKind(kind)) {
            found.add(code() + " does not take a " + kind.description());
        }
        if (hazardous && !this.hazardous) {
            found.add(code() + " does not take hazardous material");
        }
        if (!mannedAt(local)) {
            found.add(code() + " is closed at " + CLOCK.format(local) + ", it is manned " + CLOCK.format(opens) + " to "
                    + CLOCK.format(closes));
        }
        if (alreadyToday >= dailyCap) {
            found.add(code() + " has taken its " + dailyCap + " cars for the day");
        }
        return List.copyOf(found);
    }

    /** How many more cars the connection will take today, never fewer than none. */
    public int roomLeft(int alreadyToday) {
        requireACount(alreadyToday);
        return Math.max(dailyCap - alreadyToday, 0);
    }

    private static void requireACount(int alreadyToday) {
        if (alreadyToday < 0) {
            throw new AgreementError("a count of cars is not negative", Details.of("given", alreadyToday));
        }
    }

    /** {@code ESC/CN 07:00 to 19:00 cap 40}. */
    @Override
    public String toString() {
        return code() + " " + CLOCK.format(opens) + " to " + CLOCK.format(closes) + " cap " + dailyCap;
    }
}
