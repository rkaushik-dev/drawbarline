package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.carhire.Liability;
import io.github.rkaushikdev.drawbarline.carhire.Reclaim;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.demurrage.Counting;
import io.github.rkaushikdev.drawbarline.demurrage.Detention;
import io.github.rkaushikdev.drawbarline.demurrage.Invoice;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.OnTrack;
import io.github.rkaushikdev.drawbarline.events.Spans;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.network.Station;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Closing a month.
 *
 * <p>The order matters and it is the order a clerk would work in. Car hire first, because it is
 * the biggest number and the one most likely to be wrong. Then the reclaims that come off it. Then
 * demurrage, which is a different set of customers and does not touch the road accounts at all.
 * Then the statements, and only then the month is shut.
 *
 * <p>Nothing here computes anything. Every number comes from the module that owns it, and this
 * class decides what runs, in what order, and what gets posted where. The car hire run lives in
 * one place, {@link Liability#run}, so a close and a liability report cannot drift apart. That
 * separation is the reason a close can be run twice against the same books and produce the same
 * answer, and the reason a disagreement can be chased into one module rather than into a thousand
 * line procedure.
 */
public final class Close {

    private Close() {}

    /**
     * Every reclaim the month supports, on foreign cars only: a car the register has no record of
     * is passed over, and so is a car we hold ourselves at the end of the month.
     */
    public static List<Reclaim> reclaimsIn(Books books, Period period) {
        List<Reclaim> found = new ArrayList<>();
        for (CarMark carMark : books.log().cars()) {
            if (!books.fleet().has(carMark)) {
                continue;
            }
            Car car = books.fleet().get(carMark);
            if (books.fleet().ownership(carMark).holderOn(period.lastDay()).equals(books.home())) {
                continue;
            }
            found.addAll(Reclaim.reclaimsFor(car, books.log().stream(carMark), books.rates(), period.window()));
        }
        return List.copyOf(found);
    }

    /** Every placement that touched a month, counted against the tariff in force at its end. */
    public static List<Detention> detentionsIn(Books books, Period period) {
        Tariff tariff = books.tariffs().inEffectOn(period.lastDay());
        List<Detention> found = new ArrayList<>();
        for (CarMark carMark : books.log().cars()) {
            CarStream stream = books.log().stream(carMark);
            for (OnTrack span : Spans.onTrackSpans(stream, period.window().end())) {
                if (!span.interval().overlaps(period.window())) {
                    continue;
                }
                Industry track = books.customers().industry(span.industry());
                Station station = books.network().station(track.station());
                boolean loaded = span.openedBy().waybill().isPresent();
                found.add(Counting.count(span, tariff, station, loaded));
            }
        }
        return List.copyOf(found);
    }

    /** One invoice a shipper, in shipper order, numbered from {@code firstNumber}. */
    public static List<Invoice> invoicesFrom(Books books, Period period, List<Detention> detentions, int firstNumber) {
        Tariff tariff = books.tariffs().inEffectOn(period.lastDay());
        Map<String, List<Detention>> grouped = new TreeMap<>();
        for (Detention one : detentions) {
            String shipper =
                    books.customers().industry(one.industry()).shipper().code();
            grouped.computeIfAbsent(shipper, code -> new ArrayList<>()).add(one);
        }
        List<Invoice> found = new ArrayList<>();
        int offset = 0;
        for (Map.Entry<String, List<Detention>> shipper : grouped.entrySet()) {
            found.add(Invoice.assemble(
                    firstNumber + offset, shipper.getKey(), period.firstDay(), shipper.getValue(), tariff));
            offset++;
        }
        return List.copyOf(found);
    }

    /**
     * Work a month through in the order a clerk would, then shut it: car hire posted to each road
     * owed, reclaims posted back against the road holding the car, demurrage counted and invoiced,
     * a statement cut for every road with an account, and the month closed.
     */
    public static CloseResult closeMonth(
            Books books, Period period, int firstInvoice, int firstStatement, LocalDate issued) {
        books.periods().requireOpen(period);

        Liability owed = Liability.run(books.fleet(), books.log(), books.rates(), period.window(), books.home());
        List<Posting> postings = new ArrayList<>();

        List<Map.Entry<ReportingMark, Money>> byRoad = owed.byRoad().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .toList();
        for (Map.Entry<ReportingMark, Money> road : byRoad) {
            if (road.getValue().isZero()) {
                continue;
            }
            postings.add(books.ledger()
                    .post(new Posting(road.getKey(), period, PostingKind.CAR_HIRE, road.getValue(), period.lastDay())));
        }

        List<Reclaim> reclaims = reclaimsIn(books, period);
        for (Reclaim reclaim : reclaims) {
            if (reclaim.amount().isZero()) {
                continue;
            }
            ReportingMark road = books.fleet().ownership(reclaim.car()).holderOn(period.lastDay());
            postings.add(books.ledger()
                    .post(new Posting(
                            road, period, PostingKind.RECLAIM, reclaim.amount().negate(), period.lastDay())));
        }

        List<Detention> detentions = detentionsIn(books, period);
        List<Invoice> invoices = invoicesFrom(books, period, detentions, firstInvoice);

        List<Statement> statements = Statement.cutAll(books.ledger(), period, firstStatement, issued);
        books.periods().close(period);
        return new CloseResult(period, owed, reclaims, detentions, invoices, statements, postings);
    }
}
