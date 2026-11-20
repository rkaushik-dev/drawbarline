package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.util.Text;

/**
 * The kinds of thing that happen to a car.
 *
 * <p>There are not many of them and the list closes. Every hour of car hire and every day of
 * demurrage on this railroad is the gap between two of these, so a kind that nobody can start or
 * stop an obligation with does not belong here.
 *
 * <p>Each kind carries what it demands. An interchange has to name the road on the other side of
 * it, a placement has to name the track, and a movement has to carry the distance. Those are the
 * reason the event can be billed, so they are checked where the event is built and not where it is
 * used.
 */
public enum EventKind {
    RECEIVED("received in interchange"),
    DELIVERED("delivered in interchange"),
    MOVED("moved between stations"),
    NOTIFIED("consignee notified of arrival"),
    PLACED("placed at an industry"),
    CONSTRUCTIVE("constructively placed"),
    RELEASED("released by the shipper"),
    PULLED("pulled off the industry track"),
    BAD_ORDERED("set out bad order"),
    REPAIRED("released from repair"),
    HELD("held under embargo"),
    FREED("released from hold"),
    WEIGHED("weighed");

    private final String text;

    EventKind(String text) {
        this.text = text;
    }

    /** What the kind means, in words a clerk would use. */
    public String text() {
        return text;
    }

    /** An interchange is with somebody, and the somebody has to be named. */
    public boolean needsRoad() {
        return this == RECEIVED || this == DELIVERED;
    }

    /** A placement, a release and a pull name the track they happened on. */
    public boolean needsIndustry() {
        return this == PLACED || this == CONSTRUCTIVE || this == RELEASED || this == PULLED;
    }

    /** A movement carries the distance it covered. */
    public boolean needsDistance() {
        return this == MOVED;
    }

    /** Our liability to the owner begins when we take the car. */
    public boolean startsCarHire() {
        return this == RECEIVED;
    }

    public boolean endsCarHire() {
        return this == DELIVERED;
    }

    /** Free time begins at placement, actual or constructive. */
    public boolean startsDemurrage() {
        return isPlacement();
    }

    public boolean endsDemurrage() {
        return this == RELEASED;
    }

    public boolean isPlacement() {
        return this == PLACED || this == CONSTRUCTIVE;
    }

    public boolean isInterchange() {
        return needsRoad();
    }

    /** Read a kind off a file or a command line by its name, in any case. */
    public static EventKind kindNamed(String name) {
        String wanted = Text.upper(Text.strip(name));
        for (EventKind kind : values()) {
            if (kind.name().equals(wanted)) {
                return kind;
            }
        }
        throw new EventError("no event kind by that name", Details.of("name", name));
    }

    /** The name in lower case: {@code bad_ordered}. */
    @Override
    public String toString() {
        return Text.lower(name());
    }
}
