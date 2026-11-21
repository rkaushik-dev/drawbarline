package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.PostingRow;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.Posting;
import io.github.rkaushikdev.drawbarline.settlement.PostingKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;

/**
 * Every posting against every road.
 *
 * <p>The ledger writes postings and never touches one again: a posting that turns out to be wrong is
 * answered with another posting, because the month it was in has been agreed with another railroad.
 * It asks the {@link PeriodRepository period register} before it accepts anything.
 *
 * <p>The ledger loads whole rather than answering questions one at a time. A month has a few hundred
 * postings at most and the close reads all of them anyway. Loading puts the postings back without
 * asking whether their months are open, because a year of history is mostly months that are closed.
 */
public final class LedgerRepository {

    private static final String IN_ORDER = " order by p.periodYear, p.periodMonth, p.postedOn";

    private final EntityManager session;

    public LedgerRepository(EntityManager session) {
        this.session = session;
    }

    static Posting postingFromRow(PostingRow row) {
        return new Posting(
                row.getRoad(),
                new Period(row.getPeriodYear(), row.getPeriodMonth()),
                Stored.named(PostingKind.class, row.getKind()),
                row.getAmount(),
                row.getPostedOn(),
                row.getReference());
    }

    static PostingRow rowFromPosting(Posting posting) {
        return new PostingRow(
                posting.road(),
                posting.period().year(),
                posting.period().month(),
                posting.kind().name(),
                posting.amount(),
                posting.on(),
                posting.reference());
    }

    /** The months, closed and reopened. */
    public PeriodRepository periods() {
        return new PeriodRepository(session);
    }

    /** Post to a road's account, refusing a month that is closed. */
    public Posting post(Posting posting) {
        if (!periods().isOpen(posting.period())) {
            throw new SettlementError(
                    "that month is closed to posting",
                    Details.of("period", posting.period().toString()));
        }
        session.persist(rowFromPosting(posting));
        session.flush();
        return posting;
    }

    /** Every posting, by month and then by the day it was posted. */
    public List<Posting> postings() {
        return mapped(session.createQuery("select p from PostingRow p" + IN_ORDER, PostingRow.class));
    }

    /** Every posting against one road, by month and then by the day it was posted. */
    public List<Posting> postings(ReportingMark road) {
        return mapped(
                session.createQuery("select p from PostingRow p where p.road = :road" + IN_ORDER, PostingRow.class)
                        .setParameter("road", road));
    }

    private static List<Posting> mapped(TypedQuery<PostingRow> query) {
        return query.getResultList().stream()
                .map(LedgerRepository::postingFromRow)
                .toList();
    }

    /** Every road with a posting, in mark order. */
    public List<ReportingMark> roads() {
        return session.createQuery("select distinct p.road from PostingRow p order by p.road", ReportingMark.class)
                .getResultList();
    }

    /** Load the whole account, which is what a statement is cut from. */
    public Ledger ledger() {
        Ledger found = new Ledger(periods().register());
        for (Posting posting : postings()) {
            found.restore(posting);
        }
        return found;
    }

    /** How many postings are on file. */
    public int size() {
        return Counting.countOf(session, PostingRow.class);
    }
}
