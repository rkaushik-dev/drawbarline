package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.persistence.models.ClosedPeriodRow;
import io.github.rkaushikdev.drawbarline.persistence.models.ReopeningRow;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.PeriodRegister;
import io.github.rkaushikdev.drawbarline.settlement.Reopening;
import io.github.rkaushikdev.drawbarline.util.Text;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Which months are shut, and every time one was opened again.
 *
 * <p>The period register writes closings and reopenings, and it is what the ledger asks before it
 * accepts a posting. A closed month is a row, so a month nobody has closed is simply absent, and a
 * reopening takes the closing row out and leaves a row of its own with the reason on it.
 */
public final class PeriodRepository {

    private final EntityManager session;

    public PeriodRepository(EntityManager session) {
        this.session = session;
    }

    private Optional<ClosedPeriodRow> closedRow(Period period) {
        return session.createQuery(
                        "select c from ClosedPeriodRow c where c.periodYear = :year and c.periodMonth = :month",
                        ClosedPeriodRow.class)
                .setParameter("year", period.year())
                .setParameter("month", period.month())
                .getResultStream()
                .findFirst();
    }

    /** Shut a month to posting. A month already closed is refused. */
    public Period close(Period period, LocalDate on) {
        if (!isOpen(period)) {
            throw new SettlementError("that month is already closed", Details.of("period", period.toString()));
        }
        session.persist(new ClosedPeriodRow(period.year(), period.month(), on));
        session.flush();
        return period;
    }

    /** Open a closed month again, with the reason on file. */
    public Period reopen(Period period, LocalDate on, String reason) {
        if (Text.isBlank(reason)) {
            throw new SettlementError("reopening a month needs a reason", Details.of("period", period.toString()));
        }
        ClosedPeriodRow found = closedRow(period)
                .orElseThrow(
                        () -> new SettlementError("that month is not closed", Details.of("period", period.toString())));
        session.remove(found);
        session.flush();
        session.persist(new ReopeningRow(period.year(), period.month(), on, reason));
        session.flush();
        return period;
    }

    public boolean isOpen(Period period) {
        return closedRow(period).isEmpty();
    }

    /** Every closed month, in order. */
    public List<Period> closedPeriods() {
        return session
                .createQuery(
                        "select c from ClosedPeriodRow c order by c.periodYear, c.periodMonth", ClosedPeriodRow.class)
                .getResultList()
                .stream()
                .map(row -> new Period(row.getPeriodYear(), row.getPeriodMonth()))
                .toList();
    }

    /** Every reopening, in the order they happened. */
    public List<Reopening> reopenings() {
        return session
                .createQuery("select r from ReopeningRow r order by r.reopenedOn", ReopeningRow.class)
                .getResultList()
                .stream()
                .map(row -> new Reopening(
                        new Period(row.getPeriodYear(), row.getPeriodMonth()), row.getReopenedOn(), row.getReason()))
                .toList();
    }

    /** The register in memory of the months that are closed, which a ledger asks before posting. */
    public PeriodRegister register() {
        PeriodRegister register = new PeriodRegister();
        for (Period period : closedPeriods()) {
            register.close(period);
        }
        return register;
    }
}
