package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;

/** The letter that opens a mechanical designation and says what the car is. */
public enum CarKind {
    BOX("X", "box car"),
    COVERED_HOPPER("L", "covered hopper"),
    OPEN_HOPPER("H", "open top hopper"),
    GONDOLA("G", "gondola"),
    FLAT("F", "flat car"),
    TANK("T", "tank car"),
    REFRIGERATOR("R", "refrigerator car"),
    VEHICULAR("V", "vehicular flat"),
    STACK("S", "well car"),
    MAINTENANCE("M", "maintenance of way");

    private final String letter;
    private final String description;

    CarKind(String letter, String description) {
        this.letter = letter;
        this.description = description;
    }

    public String letter() {
        return letter;
    }

    public String description() {
        return description;
    }

    /** Maintenance of way equipment moves, but nobody ships in it. */
    public boolean carriesLading() {
        return this != MAINTENANCE;
    }

    /** The kind a letter stands for. */
    public static CarKind fromLetter(String letter) {
        for (CarKind kind : values()) {
            if (kind.letter.equals(letter)) {
                return kind;
            }
        }
        throw new EquipmentError("no car kind carries that letter", Details.of("letter", letter));
    }
}
