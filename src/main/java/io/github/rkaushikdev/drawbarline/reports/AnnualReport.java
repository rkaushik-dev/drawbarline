package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.settlement.Account;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.PostingKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The year on one page.
 *
 * <p>Twelve rows, one a month, and the columns are the kinds of charge. It is the only report that
 * reads more than one month, and it is what goes to the board once a year and to the auditors once
 * a year after that.
 *
 * <p>Nothing is recomputed. Every figure is the ledger read back, which means the year summary and
 * the twelve statements that made it cannot disagree, and a month that was reopened and closed
 * again shows the figure it ended at rather than the one it was first agreed at.
 */
public final class AnnualReport {

    /** The kinds of charge the summary has a column for, in column order. */
    public static final List<PostingKind> SHOWN = List.of(
            PostingKind.CAR_HIRE,
            PostingKind.MILEAGE,
            PostingKind.RECLAIM,
            PostingKind.REPAIRS,
            PostingKind.ADJUSTMENT,
            PostingKind.PAYMENT);

    private AnnualReport() {}

    /** The twelve months of a year, refusing a year we would never settle. */
    public static List<Period> monthsOf(int year) {
        if (year < 1900 || year > 2200) {
            throw new SettlementError("that is not a year we settle", Details.of("year", year));
        }
        List<Period> found = new ArrayList<>(Period.MONTHS_IN_A_YEAR);
        for (int month = 1; month <= Period.MONTHS_IN_A_YEAR; month++) {
            found.add(new Period(year, month));
        }
        return List.copyOf(found);
    }

    /** One line a month for the whole pool. */
    public static List<MonthLine> annual(Ledger ledger, int year) {
        return linesFor(ledger, year, ledger.roads());
    }

    /** One line a month for one road. */
    public static List<MonthLine> annual(Ledger ledger, int year, ReportingMark road) {
        return linesFor(ledger, year, List.of(road));
    }

    /** The year on one page for the whole pool, a month a row. */
    public static List<String> annualReport(Ledger ledger, int year) {
        return page(annual(ledger, year), year, "all roads");
    }

    /** The year on one page for one road, a month a row. */
    public static List<String> annualReport(Ledger ledger, int year, ReportingMark road) {
        return page(annual(ledger, year, road), year, road.toString());
    }

    /** The month that moved the most money either way, which is where an auditor starts. */
    public static Optional<MonthLine> busiestMonth(List<MonthLine> lines) {
        return lines.stream()
                .filter(one -> !one.movement().isZero())
                .max(Comparator.comparingLong(one -> one.movement().abs().cents()));
    }

    private static List<MonthLine> linesFor(Ledger ledger, int year, List<ReportingMark> roads) {
        List<MonthLine> found = new ArrayList<>();
        for (Period period : monthsOf(year)) {
            Map<PostingKind, Money> amounts = new LinkedHashMap<>();
            int postings = 0;
            for (ReportingMark one : roads) {
                Account account = ledger.account(one);
                postings += account.postings(period).size();
                account.byKind(period).forEach((kind, amount) -> amounts.merge(kind, amount, Money::plus));
            }
            found.add(new MonthLine(period, amounts, postings));
        }
        return List.copyOf(found);
    }

    private static List<String> page(List<MonthLine> lines, int year, String who) {
        List<Column> columns = new ArrayList<>();
        columns.add(new Column("month"));
        columns.add(new Column("postings", Align.RIGHT));
        for (PostingKind kind : SHOWN) {
            columns.add(new Column(kind.text(), Align.RIGHT));
        }
        columns.add(new Column("movement", Align.RIGHT));
        Table table = new Table(columns);

        for (MonthLine one : lines) {
            List<String> row = new ArrayList<>();
            row.add(one.period().label());
            row.add(String.valueOf(one.postings()));
            for (PostingKind kind : SHOWN) {
                row.add(Render.money(one.amountOf(kind)));
            }
            row.add(Render.money(one.movement()));
            table.add(row.toArray(String[]::new));
        }

        List<String> total = new ArrayList<>();
        total.add(String.valueOf(year));
        total.add(String.valueOf(lines.stream().mapToInt(MonthLine::postings).sum()));
        for (PostingKind kind : SHOWN) {
            total.add(Render.money(
                    Money.total(lines.stream().map(one -> one.amountOf(kind)).toList())));
        }
        total.add(
                Render.money(Money.total(lines.stream().map(MonthLine::movement).toList())));
        table.total(total.toArray(String[]::new));

        List<String> out = new ArrayList<>(Render.heading("annual summary", year + " " + who));
        out.addAll(table.lines());
        return List.copyOf(out);
    }
}
