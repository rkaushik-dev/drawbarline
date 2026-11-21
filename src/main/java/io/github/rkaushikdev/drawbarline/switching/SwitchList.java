package io.github.rkaushikdev.drawbarline.switching;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Everything the local does at one station on one day, in the order the crew does it. */
public record SwitchList(String station, LocalDate day, List<Move> moves) {

    public SwitchList {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(day, "day");
        moves = List.copyOf(moves);
    }

    public List<Move> pulls() {
        return ofKind(MoveKind.PULL);
    }

    public List<Move> placements() {
        return ofKind(MoveKind.PLACE);
    }

    public List<Move> constructive() {
        return ofKind(MoveKind.CONSTRUCTIVE);
    }

    /** Every move the crew does not make: stopped cars, tracks not switched, constructive placements. */
    public List<Move> stopped() {
        return moves.stream().filter(one -> !one.kind().worked()).toList();
    }

    /** How many cars the crew actually touches. */
    public int worked() {
        return (int) moves.stream().filter(one -> one.kind().worked()).count();
    }

    /** How many lines the list has. */
    public int size() {
        return moves.size();
    }

    private List<Move> ofKind(MoveKind kind) {
        return moves.stream().filter(one -> one.kind() == kind).toList();
    }
}
