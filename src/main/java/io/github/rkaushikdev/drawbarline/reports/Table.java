package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A heading, some rows, and however many total lines a report wants, laid out in fixed width
 * columns separated by two spaces with a rule under the heading and another over the totals.
 */
public final class Table {

    private final List<Column> columns;
    private final List<List<String>> rows = new ArrayList<>();
    private final List<List<String>> totals = new ArrayList<>();

    public Table(List<Column> columns) {
        this.columns = List.copyOf(columns);
    }

    public List<Column> columns() {
        return columns;
    }

    public List<List<String>> rows() {
        return Collections.unmodifiableList(rows);
    }

    public List<List<String>> totals() {
        return Collections.unmodifiableList(totals);
    }

    /** Add a row, one value a column. */
    public void add(String... values) {
        if (values.length != columns.size()) {
            throw new ConfigurationError(
                    "that row does not fit the columns",
                    Details.of("columns", columns.size(), "values", values.length));
        }
        rows.add(List.of(values));
    }

    /** Add a total line, one value a column. */
    public void total(String... values) {
        if (values.length != columns.size()) {
            throw new ConfigurationError(
                    "that total does not fit the columns",
                    Details.of("columns", columns.size(), "values", values.length));
        }
        totals.add(List.of(values));
    }

    /** The width of every column, wide enough for its heading, its rows and its totals. */
    public List<Integer> widths() {
        List<Integer> found = new ArrayList<>(columns.size());
        for (int at = 0; at < columns.size(); at++) {
            List<String> seen = new ArrayList<>();
            for (List<String> row : rows) {
                seen.add(row.get(at));
            }
            for (List<String> row : totals) {
                seen.add(row.get(at));
            }
            found.add(columns.get(at).sized(seen));
        }
        return List.copyOf(found);
    }

    /** The report body, one string a line. */
    public List<String> lines() {
        List<Integer> widths = widths();
        List<String> out = new ArrayList<>();
        List<String> headings = new ArrayList<>(columns.size());
        for (Column column : columns) {
            headings.add(column.heading());
        }
        out.add(line(headings, widths));
        out.add(rule(widths));
        for (List<String> row : rows) {
            out.add(line(row, widths));
        }
        if (!totals.isEmpty()) {
            out.add(rule(widths));
            for (List<String> row : totals) {
                out.add(line(row, widths));
            }
        }
        return List.copyOf(out);
    }

    private String line(List<String> values, List<Integer> widths) {
        List<String> laid = new ArrayList<>(columns.size());
        for (int at = 0; at < columns.size(); at++) {
            laid.add(columns.get(at).lay(values.get(at), widths.get(at)));
        }
        return String.join(Render.GAP, laid);
    }

    private static String rule(List<Integer> widths) {
        List<String> parts = new ArrayList<>(widths.size());
        for (int width : widths) {
            parts.add(Render.RULE.repeat(width));
        }
        return String.join(Render.GAP, parts);
    }

    /** The lines joined by line feeds. */
    @Override
    public String toString() {
        return String.join("\n", lines());
    }
}
