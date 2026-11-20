package io.github.rkaushikdev.drawbarline.days;

import java.time.LocalDate;

/** A day the tariff does not charge for: the date it falls on and the date the office keeps it. */
public record Holiday(String name, LocalDate falls, LocalDate kept) {

    /** Whether the office keeps it on a different day than it falls. */
    public boolean moved() {
        return !falls.equals(kept);
    }
}
