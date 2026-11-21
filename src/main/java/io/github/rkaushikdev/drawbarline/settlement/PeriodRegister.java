package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which months are closed, and every time one was opened again.
 *
 * <p>A period is open until it is closed, and then nothing may be posted to it. A month reopened
 * and closed again is not the same as a month that was never closed, and the register remembers
 * the difference.
 */
public final class PeriodRegister {

    private final Set<Period> closed = new HashSet<>();
    private final List<Reopening> reopenings = new ArrayList<>();

    public Period close(Period period) {
        if (closed.contains(period)) {
            throw new SettlementError("that month is already closed", Details.of("period", period.toString()));
        }
        closed.add(period);
        return period;
    }

    /** Open a closed month again, with the reason on file. A reason that will not stand leaves the month closed. */
    public Reopening reopen(Period period, LocalDate on, String reason) {
        if (!closed.contains(period)) {
            throw new SettlementError("that month is not closed", Details.of("period", period.toString()));
        }
        closed.remove(period);
        Reopening found = new Reopening(period, on, reason);
        reopenings.add(found);
        return found;
    }

    public boolean isOpen(Period period) {
        return !closed.contains(period);
    }

    /** The period, or a refusal when it is closed to posting. */
    public Period requireOpen(Period period) {
        if (closed.contains(period)) {
            throw new SettlementError("that month is closed to posting", Details.of("period", period.toString()));
        }
        return period;
    }

    /** Every closed month, in order. */
    public List<Period> closedPeriods() {
        return closed.stream().sorted().toList();
    }

    /** Every reopening, in the order they happened. */
    public List<Reopening> reopenings() {
        return List.copyOf(reopenings);
    }

    /** The reopenings of one month, in the order they happened. */
    public List<Reopening> reopenings(Period period) {
        return reopenings.stream().filter(one -> one.period().equals(period)).toList();
    }

    public boolean wasEverReopened(Period period) {
        return reopenings.stream().anyMatch(one -> one.period().equals(period));
    }
}
