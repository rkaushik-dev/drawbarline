package io.github.rkaushikdev.drawbarline.service;

import io.github.rkaushikdev.drawbarline.agreements.InterchangeAgreement;
import io.github.rkaushikdev.drawbarline.audit.Action;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.interchange.Delivery;
import io.github.rkaushikdev.drawbarline.interchange.Verdict;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.persistence.Numbering;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.settlement.Books;
import io.github.rkaushikdev.drawbarline.settlement.Close;
import io.github.rkaushikdev.drawbarline.settlement.CloseResult;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.Posting;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The application layer.
 *
 * <p>Everything below this class works on objects and knows nothing about a database. Everything
 * above it, the command line and the HTTP API, knows about a database and nothing about how car
 * hire is worked out. This is the one place the two meet, and it is deliberately thin: load the
 * registers, run the thing the domain already knows how to do, write back what came out of it.
 *
 * <p>Every decision a person makes goes through here and every one of them writes an audit entry
 * as it goes. The actor defaults to a placeholder rather than being required, because a scheduled
 * close genuinely has nobody behind it and recording that honestly is better than recording
 * somebody who was asleep.
 *
 * <p>The close is the interesting one. It runs against an in memory ledger loaded out of the
 * postings table, produces the postings it wants to make, and only then are they written. That
 * means a close that blows up half way leaves the month exactly as it was, and it means the same
 * close can be run against a ledger built in a test with no database at all.
 */
public final class Service {

    /** Who an audit entry names when nobody signed for the decision. */
    public static final String UNSIGNED = "unsigned";

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private Service() {}

    /** Load every register out of the database into one working set, on our own line. */
    public static Books booksOf(UnitOfWork work) {
        return booksOf(work, Reference.drawbarLine(), Reference.DRAWBAR_LINE.mark());
    }

    /** Load every register out of the database into one working set, on a given network. */
    public static Books booksOf(UnitOfWork work, Network network, ReportingMark home) {
        return new Books(
                network,
                work.fleet().register(),
                work.events().log(),
                work.rates().table(),
                work.tariffs().book(),
                work.customers().register(),
                work.ledger().ledger(),
                work.periods().register(),
                home);
    }

    /** Close a month with nobody signed for it. */
    public static CloseResult runClose(UnitOfWork work, Period period, LocalDate issued) {
        return runClose(work, period, issued, UNSIGNED);
    }

    /** Close a month on our own line, with the person who ran it on file. */
    public static CloseResult runClose(UnitOfWork work, Period period, LocalDate issued, String actor) {
        return runClose(work, period, issued, actor, Reference.drawbarLine(), Reference.DRAWBAR_LINE.mark());
    }

    /**
     * Close a month against the database, numbering the documents as it goes. Nothing is written
     * until the close has worked the whole month through, and the caller commits.
     */
    public static CloseResult runClose(
            UnitOfWork work, Period period, LocalDate issued, String actor, Network network, ReportingMark home) {
        Books books = booksOf(work, network, home);
        Set<String> shippers = new HashSet<>();
        for (Industry track : books.customers()) {
            shippers.add(track.shipper().code());
        }
        int firstInvoice = work.numbering()
                .takeMany(Numbering.INVOICES, Math.max(shippers.size(), 1))
                .get(0);
        int firstStatement = work.numbering()
                .takeMany(Numbering.STATEMENTS, Math.max(books.ledger().roads().size(), 1))
                .get(0);

        CloseResult result = Close.closeMonth(books, period, firstInvoice, firstStatement, issued);

        for (Posting posting : result.postings()) {
            work.ledger().post(posting);
        }
        work.periods().close(period, issued);
        work.audit()
                .record(
                        actor,
                        Action.CLOSED_PERIOD,
                        period.label(),
                        result.posted() + " postings, car hire " + result.carHire());
        return result;
    }

    /** Open a closed month again with nobody signed for it. */
    public static Period reopenPeriod(UnitOfWork work, Period period, LocalDate on, String reason) {
        return reopenPeriod(work, period, on, reason, UNSIGNED);
    }

    /** Open a closed month again, with the reason and the person on file. */
    public static Period reopenPeriod(UnitOfWork work, Period period, LocalDate on, String reason, String actor) {
        work.periods().reopen(period, on, reason);
        work.audit().record(actor, Action.REOPENED_PERIOD, period.label(), reason);
        return period;
    }

    /** File an interchange agreement with nobody signed for it. */
    public static InterchangeAgreement signAgreement(UnitOfWork work, InterchangeAgreement agreement) {
        return signAgreement(work, agreement, UNSIGNED);
    }

    /** File an interchange agreement and record who filed it. */
    public static InterchangeAgreement signAgreement(UnitOfWork work, InterchangeAgreement agreement, String actor) {
        work.agreements().sign(agreement);
        work.audit()
                .record(
                        actor,
                        Action.SIGNED_AGREEMENT,
                        agreement.code(),
                        "cap " + agreement.dailyCap() + ", " + agreement.opens().format(CLOCK) + " to "
                                + agreement.closes().format(CLOCK));
        return agreement;
    }

    /** Ask about a car carrying nothing in particular, on our own line. */
    public static Verdict deliveryCheck(
            UnitOfWork work, CarMark car, String junction, ReportingMark road, LocalDateTime local) {
        return deliveryCheck(work, car, junction, road, local, Optional.empty());
    }

    /** Ask about a car and what it carries, on our own line. */
    public static Verdict deliveryCheck(
            UnitOfWork work,
            CarMark car,
            String junction,
            ReportingMark road,
            LocalDateTime local,
            Optional<Commodity> commodity) {
        return deliveryCheck(work, car, junction, road, local, commodity, Reference.drawbarLine());
    }

    /**
     * Ask the equipment, the embargoes and the agreement in one go.
     *
     * @param local the reading on the clock at the junction when the car is offered
     */
    public static Verdict deliveryCheck(
            UnitOfWork work,
            CarMark car,
            String junction,
            ReportingMark road,
            LocalDateTime local,
            Optional<Commodity> commodity,
            Network network) {
        return Delivery.canDeliver(
                car,
                work.fleet().register(),
                work.events().log(),
                work.embargoes().register(),
                work.agreements().at(junction, road, local.toLocalDate()),
                network.station(junction),
                local,
                commodity);
    }
}
