package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one road owes us, or we owe them, for one month: one page a road a month.
 *
 * <p>What was outstanding when the month began, what happened during it grouped by kind, and what
 * is outstanding now. The other road cuts the same statement from their own books and the two are
 * compared, which only works if both sides agree on what the opening balance was. So the opening
 * balance is computed from everything posted through the previous month and never carried in a
 * field somebody can edit.
 *
 * <p>The statement says which way the money runs in words as well as in a sign, because the sign
 * is a convention this office keeps and the road reading it keeps the opposite one.
 */
public record Statement(
        int number, ReportingMark road, Period period, LocalDate issued, Money opening, List<StatementLine> lines) {

    public Statement {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(opening, "opening");
        lines = List.copyOf(lines);
        if (number < 1) {
            throw new SettlementError("a statement is numbered from one", Details.of("number", number));
        }
        if (issued.isBefore(period.lastDay())) {
            throw new SettlementError(
                    "a statement is cut after the month it covers",
                    Details.of("period", period.toString(), "issued", issued.toString()));
        }
    }

    /** What happened during the month, every kind together. */
    public Money movement() {
        return Money.total(lines.stream().map(StatementLine::amount).toList());
    }

    /** What is outstanding at the end of the month. */
    public Money closing() {
        return opening.plus(movement());
    }

    /** Whether we write the cheque. */
    public boolean wePay() {
        return closing().cents() > 0;
    }

    /** Whether nobody owes anybody. */
    public boolean isSquare() {
        return closing().isZero();
    }

    /** The month's amount for one kind, nothing when the kind has no line. */
    public Money amountFor(PostingKind kind) {
        for (StatementLine line : lines) {
            if (line.kind() == kind) {
                return line.amount();
            }
        }
        return Money.ZERO;
    }

    /** Which way the money runs, in words: {@code we pay CN}, {@code CN pays us} or {@code square}. */
    public String direction() {
        if (isSquare()) {
            return "square";
        }
        if (wePay()) {
            return "we pay " + road;
        }
        return road + " pays us";
    }

    /** {@code statement 9001 CN 2025-01 $780.00 we pay CN}. */
    @Override
    public String toString() {
        return "statement " + number + " " + road + " " + period + " "
                + closing().abs() + " " + direction();
    }

    /** Cut the statement for one road and one month out of the ledger. */
    public static Statement cut(Ledger ledger, ReportingMark road, Period period, int number, LocalDate issued) {
        Account account = ledger.account(road);
        Money opening = account.balanceThrough(period.preceding());
        Map<PostingKind, Money> byKind = account.byKind(period);
        List<StatementLine> lines = new ArrayList<>();
        for (PostingKind kind : PostingKind.values()) {
            if (byKind.containsKey(kind)) {
                lines.add(new StatementLine(kind, byKind.get(kind)));
            }
        }
        return new Statement(number, road, period, issued, opening, lines);
    }

    /** A statement for every road with an account, numbered in road order from {@code firstNumber}. */
    public static List<Statement> cutAll(Ledger ledger, Period period, int firstNumber, LocalDate issued) {
        List<Statement> found = new ArrayList<>();
        List<ReportingMark> roads = ledger.roads();
        for (int offset = 0; offset < roads.size(); offset++) {
            found.add(cut(ledger, roads.get(offset), period, firstNumber + offset, issued));
        }
        return List.copyOf(found);
    }
}
