package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.settlement.Account;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.PostingKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The trial balance, which is the report that proves the others.
 *
 * <p>Every other report answers a question. This one checks an identity: the closing balances on
 * the roads, added up, equal the position of the whole pool. If they do not, something has been
 * posted to a road that is not on the account list, and every number on every other page is
 * suspect.
 *
 * <p>It is the cheapest possible check and it is worth running before anything is sent out. A
 * month that does not balance is a month somebody looks at rather than a month somebody posts.
 */
public final class LedgerReport {

    private LedgerReport() {}

    /** One line a road, opening through closing, in mark order. */
    public static List<BalanceLine> trialBalance(Ledger ledger, Period period) {
        List<BalanceLine> found = new ArrayList<>();
        for (ReportingMark road : ledger.roads()) {
            Account account = ledger.account(road);
            Money opening = account.balanceThrough(period.preceding());
            Money movement = account.balance(period);
            found.add(new BalanceLine(
                    road,
                    opening,
                    movement,
                    opening.plus(movement),
                    account.postings(period).size()));
        }
        return List.copyOf(found);
    }

    /** Whether every line balances and the road closings add up to the whole pool through the month. */
    public static boolean balances(List<BalanceLine> lines, Ledger ledger, Period period) {
        if (!lines.stream().allMatch(BalanceLine::balances)) {
            return false;
        }
        return closingOf(lines).equals(ledger.positionThrough(period));
    }

    /** The trial balance, with the identity checked at the bottom. */
    public static List<String> trialBalanceReport(Ledger ledger, Period period) {
        List<BalanceLine> lines = trialBalance(ledger, period);
        Table table = new Table(List.of(
                new Column("road"),
                new Column("postings", Align.RIGHT),
                new Column("opening", Align.RIGHT),
                new Column("movement", Align.RIGHT),
                new Column("closing", Align.RIGHT)));
        for (BalanceLine one : lines) {
            table.add(
                    one.road().toString(),
                    String.valueOf(one.postings()),
                    Render.money(one.opening()),
                    Render.money(one.movement()),
                    Render.money(one.closing()));
        }
        table.total(
                lines.size() + " roads",
                String.valueOf(lines.stream().mapToInt(BalanceLine::postings).sum()),
                Render.money(
                        Money.total(lines.stream().map(BalanceLine::opening).toList())),
                Render.money(
                        Money.total(lines.stream().map(BalanceLine::movement).toList())),
                Render.money(closingOf(lines)));
        String tail = balances(lines, ledger, period) ? "balances" : "DOES NOT BALANCE";
        List<String> out = new ArrayList<>(Render.heading("trial balance", period.label()));
        out.addAll(table.lines());
        out.add("");
        out.add(tail);
        return List.copyOf(out);
    }

    /** One line a kind of charge across every road, the kinds in their published order. */
    public static List<String> byKindReport(Ledger ledger, Period period) {
        Table table = new Table(List.of(new Column("kind"), new Column("amount", Align.RIGHT)));
        Map<PostingKind, Money> running = new EnumMap<>(PostingKind.class);
        for (ReportingMark road : ledger.roads()) {
            ledger.account(road).byKind(period).forEach((kind, amount) -> running.merge(kind, amount, Money::plus));
        }
        for (Map.Entry<PostingKind, Money> kind : running.entrySet()) {
            table.add(kind.getKey().text(), Render.money(kind.getValue()));
        }
        table.total("all kinds", Render.money(Money.total(running.values())));
        List<String> out = new ArrayList<>(Render.heading("postings by kind", period.label()));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    private static Money closingOf(List<BalanceLine> lines) {
        return Money.total(lines.stream().map(BalanceLine::closing).toList());
    }
}
