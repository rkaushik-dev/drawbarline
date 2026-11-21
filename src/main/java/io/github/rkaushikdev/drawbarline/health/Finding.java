package io.github.rkaushikdev.drawbarline.health;

import java.util.Locale;
import java.util.Objects;

/**
 * One thing that was checked, how it came out, and what to do about it.
 *
 * @param check what was asked, such as {@code car hire rates}
 * @param detail what was found, such as {@code 2 bands}
 * @param remedy what to run when the finding is not good, empty when there is nothing to do
 */
public record Finding(String check, Level level, String detail, String remedy) {

    public Finding {
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(remedy, "remedy");
    }

    /** A finding with no remedy to offer. */
    public Finding(String check, Level level, String detail) {
        this(check, level, detail, "");
    }

    /**
     * The level and the check in columns, then the detail, then the remedy in brackets unless the
     * finding is good: {@code bad   car hire rates     none filed (reference rates <sheet>)}.
     */
    @Override
    public String toString() {
        String tail = !remedy.isEmpty() && level != Level.GOOD ? " (" + remedy + ")" : "";
        return String.format(Locale.ROOT, "%-5s %-18s %s%s", level.value(), check, detail, tail);
    }
}
