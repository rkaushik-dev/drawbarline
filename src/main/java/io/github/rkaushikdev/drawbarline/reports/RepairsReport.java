package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.repairs.Objection;
import io.github.rkaushikdev.drawbarline.repairs.PricedCard;
import io.github.rkaushikdev.drawbarline.repairs.Responsibility;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The repair billing reports.
 *
 * <p>Two questions get asked every month: what did each road bill us, and how much of it is ours to
 * pay rather than theirs to eat. Those are different numbers and the report shows both, because a
 * road whose cards are mostly handling damage is a road doing damage, and that is a conversation to
 * have before the bills get any bigger.
 *
 * <p>The exception report is the other side. Anything filed and not answered is money in the air,
 * and the filing clock means an exception nobody answers eventually stops being ours to argue.
 */
public final class RepairsReport {

    private RepairsReport() {}

    /** Gather priced cards by the road that wrote them, in mark order. */
    public static List<RoadTotals> byRoad(List<PricedCard> cards) {
        Map<ReportingMark, List<PricedCard>> gathered = new LinkedHashMap<>();
        for (PricedCard one : cards) {
            gathered.computeIfAbsent(one.card().performedBy(), road -> new ArrayList<>())
                    .add(one);
        }
        return gathered.entrySet().stream()
                .sorted(Comparator.comparing(pair -> pair.getKey().toString()))
                .map(pair -> totalsOf(pair.getKey(), pair.getValue()))
                .toList();
    }

    /** One line a road, with the split that matters. */
    public static List<String> billingReport(List<PricedCard> cards, String title) {
        Table table = new Table(List.of(
                new Column("road"),
                new Column("cards", Align.RIGHT),
                new Column("lines", Align.RIGHT),
                new Column("billed", Align.RIGHT),
                new Column("to owner", Align.RIGHT),
                new Column("their cost", Align.RIGHT)));
        List<RoadTotals> found = byRoad(cards);
        for (RoadTotals one : found) {
            table.add(
                    one.road().toString(),
                    String.valueOf(one.cards()),
                    String.valueOf(one.lines()),
                    Render.money(one.billed()),
                    Render.money(one.toOwner()),
                    Render.money(one.borneByTheRoad()));
        }
        table.total(
                found.size() + " roads",
                String.valueOf(found.stream().mapToInt(RoadTotals::cards).sum()),
                String.valueOf(found.stream().mapToInt(RoadTotals::lines).sum()),
                Render.money(Money.total(found.stream().map(RoadTotals::billed).toList())),
                Render.money(Money.total(found.stream().map(RoadTotals::toOwner).toList())),
                Render.money(Money.total(
                        found.stream().map(RoadTotals::borneByTheRoad).toList())));
        List<String> out = new ArrayList<>(Render.heading("repair billing", title));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    /** What each party answers for across a set of cards, every party listed. */
    public static Map<Responsibility, Money> responsibilitySplit(List<PricedCard> cards) {
        Map<Responsibility, Money> found = new EnumMap<>(Responsibility.class);
        for (Responsibility who : Responsibility.values()) {
            found.put(
                    who, Money.total(cards.stream().map(one -> one.owedBy(who)).toList()));
        }
        return Collections.unmodifiableMap(found);
    }

    /**
     * Everything filed and not answered, by card and line, with the days left to answer it. An
     * exception against a card not among {@code cards} counts its days to the day it was filed.
     */
    public static List<String> exceptionReport(
            List<Objection> objections, Map<Integer, PricedCard> cards, LocalDate asOf) {
        Table table = new Table(List.of(
                new Column("card"),
                new Column("line", Align.RIGHT),
                new Column("ground"),
                new Column("disputed", Align.RIGHT),
                new Column("filed"),
                new Column("days left", Align.RIGHT)));
        List<Objection> ordered = objections.stream()
                .sorted(Comparator.comparingInt(Objection::cardNumber).thenComparingInt(Objection::line))
                .toList();
        for (Objection one : ordered) {
            PricedCard priced = cards.get(one.cardNumber());
            LocalDate due = priced != null ? Objection.mustFileBy(priced.card().on()) : one.filedOn();
            table.add(
                    String.valueOf(one.cardNumber()),
                    String.valueOf(one.line()),
                    one.ground().text(),
                    Render.money(one.disputed()),
                    one.filedOn().toString(),
                    String.valueOf(ChronoUnit.DAYS.between(asOf, due)));
        }
        table.total(
                ordered.size() + " open",
                "",
                "",
                Render.money(
                        Money.total(ordered.stream().map(Objection::disputed).toList())),
                "",
                "");
        List<String> out = new ArrayList<>(Render.heading("open repair exceptions", asOf.toString()));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    private static RoadTotals totalsOf(ReportingMark road, List<PricedCard> found) {
        return new RoadTotals(
                road,
                found.size(),
                found.stream().mapToInt(one -> one.lines().size()).sum(),
                Money.total(found.stream().map(PricedCard::total).toList()),
                Money.total(found.stream().map(PricedCard::billedToOwner).toList()));
    }
}
