package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.persistence.models.EventRow;

/**
 * Events, in both directions.
 *
 * <p>The kind is stored as its name rather than a number, because a number would have to stay
 * stable forever and a name is what somebody reading the table with a query tool needs to see.
 */
public final class EventMappers {

    private EventMappers() {}

    public static Event eventFromRow(EventRow row) {
        return new Event(
                row.getKind(),
                row.getCarMark(),
                row.getHappened(),
                row.getStation(),
                row.getRoad(),
                row.getIndustry(),
                row.getWaybill(),
                row.getTenths(),
                row.getNote());
    }

    public static EventRow rowFromEvent(Event event) {
        return new EventRow(
                event.car(),
                event.kind(),
                event.when(),
                event.station(),
                event.road().orElse(null),
                event.industry().orElse(null),
                event.waybill().orElse(null),
                event.tenths().orElse(null),
                event.note());
    }
}
