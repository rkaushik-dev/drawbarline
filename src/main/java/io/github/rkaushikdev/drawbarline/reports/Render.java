package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.util.Numbers;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.ArrayList;
import java.util.List;

/**
 * Laying a report out as text.
 *
 * <p>Every report ends up on a green bar printer in the general office or in a mail as plain text,
 * so the output is columns of fixed width and nothing else: no box drawing, no colour, nothing
 * outside ASCII. Numbers sit right, text left, money right with its sign attached, a rule goes
 * under the heading, and a total line lines up with the column it totals.
 */
public final class Render {

    public static final String RULE = "-";
    public static final String GAP = "  ";

    private Render() {}

    /** The lines every report opens with: the title in capitals and a blank line. */
    public static List<String> heading(String title) {
        return heading(title, "");
    }

    /** The lines every report opens with: the title in capitals, a subtitle and a blank line. */
    public static List<String> heading(String title, String subtitle) {
        List<String> out = new ArrayList<>();
        out.add(Text.upper(title));
        if (!subtitle.isEmpty()) {
            out.add(subtitle);
        }
        out.add("");
        return List.copyOf(out);
    }

    public static String money(Money amount) {
        return amount.toString();
    }

    /** Hundredths of an hour as hours to two places: {@code 96.00}. */
    public static String hours(long hundredths) {
        return Numbers.fixed(hundredths / 100.0, 2);
    }

    /** Tenths of a mile as miles to one place: {@code 33.6}. */
    public static String miles(long tenths) {
        return Numbers.fixed(tenths / 10.0, 1);
    }
}
