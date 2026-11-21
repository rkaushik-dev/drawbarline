package io.github.rkaushikdev.drawbarline.export;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.audit.Entry;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.reports.BalanceLine;
import io.github.rkaushikdev.drawbarline.reports.LedgerReport;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.Posting;
import io.github.rkaushikdev.drawbarline.settlement.Statement;
import io.github.rkaushikdev.drawbarline.settlement.StatementLine;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handing a month to somebody who is not this application.
 *
 * <p>The auditors want a month, the connections occasionally want their part of one, and neither
 * of them is going to install this. So a month goes out as JSON: the postings, the statements, the
 * demurrage invoices and the events behind them, in one file, with the figures as integers in the
 * units the package uses rather than as formatted money.
 *
 * <p>Nothing is computed on the way out. Every figure is read back off the books, which means an
 * export and the statement that went out in the post carry the same numbers, and an export of a
 * month that was reopened shows where it ended rather than where it was first agreed.
 *
 * <p>The shape is versioned. Somebody will write a reader for this and the reader will outlive
 * whoever wrote it, so the file says what it is.
 *
 * <p>A bundle is plain data: maps with their keys in a fixed order, lists, text, whole numbers,
 * flags, and {@code null} where a value is absent. Any JSON writer writes it as it stands.
 */
public final class Export {

    /** What a bundle says it is. */
    public static final String SHAPE = "drawbarline.month";

    /** Which revision of the shape a bundle is written in. */
    public static final int VERSION = 1;

    /** The parts of a bundle that hold rows, in the order they are counted. */
    private static final List<String> COUNTED = List.of("postings", "statements", "events", "audit");

    private Export() {}

    /** Every posting made in the month, in the order the ledger holds them. */
    public static List<Map<String, Object>> postingsOf(UnitOfWork work, Period period) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (Posting one : work.ledger().postings()) {
            if (!one.period().equals(period)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("road", one.road().toString());
            row.put("kind", Text.lower(one.kind().name()));
            row.put("cents", one.amount().cents());
            row.put("on", one.on().toString());
            row.put("reference", one.reference());
            found.add(Collections.unmodifiableMap(row));
        }
        return List.copyOf(found);
    }

    /** The statement every road with an account would get for the month, numbered from one. */
    public static List<Map<String, Object>> statementsOf(UnitOfWork work, Period period) {
        Ledger ledger = work.ledger().ledger();
        List<Map<String, Object>> found = new ArrayList<>();
        for (Statement one : Statement.cutAll(ledger, period, 1, period.lastDay())) {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (StatementLine line : one.lines()) {
                Map<String, Object> shown = new LinkedHashMap<>();
                shown.put("kind", Text.lower(line.kind().name()));
                shown.put("cents", line.amount().cents());
                lines.add(Collections.unmodifiableMap(shown));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("road", one.road().toString());
            row.put("opening_cents", one.opening().cents());
            row.put("closing_cents", one.closing().cents());
            row.put("direction", one.direction());
            row.put("lines", List.copyOf(lines));
            found.add(Collections.unmodifiableMap(row));
        }
        return List.copyOf(found);
    }

    /** Every event inside the month, with its moment in UTC. */
    public static List<Map<String, Object>> eventsOf(UnitOfWork work, Period period) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (Event one : work.events().during(period.window())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("car", one.car().toString());
            row.put("kind", one.kind().toString());
            row.put("moment", Clock.iso(one.when()));
            row.put("station", one.station());
            row.put("road", one.road().map(ReportingMark::toString).orElse(null));
            row.put("industry", one.industry().orElse(null));
            row.put("waybill", one.waybill().orElse(null));
            row.put("tenths", one.tenths().orElse(null));
            found.add(Collections.unmodifiableMap(row));
        }
        return List.copyOf(found);
    }

    /** The whole audit trail, newest first. */
    public static List<Map<String, Object>> auditOf(UnitOfWork work) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (Entry one : work.audit().entries()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("at", Clock.iso(one.at()));
            row.put("actor", one.actor());
            row.put("action", Text.lower(one.action().name()));
            row.put("subject", one.subject());
            row.put("detail", one.detail());
            found.add(Collections.unmodifiableMap(row));
        }
        return List.copyOf(found);
    }

    /** One month, whole, as plain data, taken now. */
    public static Map<String, Object> month(UnitOfWork work, Period period) {
        return month(work, period, Clock.now());
    }

    /** One month, whole, as plain data, stamped with the moment it was taken. */
    public static Map<String, Object> month(UnitOfWork work, Period period, Instant at) {
        Ledger ledger = work.ledger().ledger();
        List<BalanceLine> lines = LedgerReport.trialBalance(ledger, period);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("shape", SHAPE);
        bundle.put("version", VERSION);
        bundle.put("taken_at", Clock.iso(at));
        bundle.put("period", period.label());
        bundle.put("closed", !work.periods().isOpen(period));
        bundle.put("balances", LedgerReport.balances(lines, ledger, period));
        bundle.put("postings", postingsOf(work, period));
        bundle.put("statements", statementsOf(work, period));
        bundle.put("events", eventsOf(work, period));
        bundle.put("audit", auditOf(work));
        return Collections.unmodifiableMap(bundle);
    }

    /** How much is in a bundle, for anybody checking one arrived whole. */
    public static Map<String, Integer> counts(Map<String, ?> bundle) {
        Map<String, Integer> found = new LinkedHashMap<>();
        for (String name : COUNTED) {
            if (!(bundle.get(name) instanceof Collection<?> rows)) {
                throw new ConfigurationError("that is not a whole bundle", Details.of("missing", name));
            }
            found.put(name, rows.size());
        }
        return Collections.unmodifiableMap(found);
    }
}
