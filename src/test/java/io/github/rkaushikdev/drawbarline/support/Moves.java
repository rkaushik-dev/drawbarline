package io.github.rkaushikdev.drawbarline.support;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;

import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;

/**
 * The moves a car makes on a small working railroad in January 2025, each recorded straight onto a
 * log: received from CN at Escanaba, delivered to CN at Marinette, moved, placed on and pulled off
 * an industry track, bad ordered and repaired, held and freed.
 */
public final class Moves {

    /** The road on the other side of every interchange here. */
    public static final ReportingMark CN = new ReportingMark("CN");

    /** The waybill a car comes in on unless a test says otherwise. */
    public static final Integer WAYBILL = 41207;

    /** The track a car is placed on unless a test says otherwise. */
    public static final String MILL_TRACK = "ESCPAP1";

    /** Interchange happens at noon. */
    public static final int INTERCHANGE_HOUR = 12;

    /** Cars are set out and repaired first thing. */
    public static final int SHOP_HOUR = 8;

    /** Holds are laid on and lifted a little later. */
    public static final int HOLD_HOUR = 9;

    private Moves() {}

    /** Received from CN at Escanaba at noon on a day in January, on the usual waybill. */
    public static Event received(EventLog log, String mark, int day) {
        return received(log, mark, day, INTERCHANGE_HOUR, WAYBILL);
    }

    public static Event received(EventLog log, String mark, int day, int hour) {
        return received(log, mark, day, hour, WAYBILL);
    }

    /** Received with a waybill of its own, or none when {@code waybill} is null. */
    public static Event received(EventLog log, String mark, int day, int hour, Integer waybill) {
        return log.record(Event.builder(EventKind.RECEIVED, CarMark.parse(mark), utc(2025, 1, day, hour), "ESC")
                .road(CN)
                .waybill(waybill)
                .build());
    }

    /** Delivered to CN at Marinette at noon. */
    public static Event delivered(EventLog log, String mark, int day) {
        return delivered(log, mark, day, INTERCHANGE_HOUR);
    }

    public static Event delivered(EventLog log, String mark, int day, int hour) {
        return log.record(Event.builder(EventKind.DELIVERED, CarMark.parse(mark), utc(2025, 1, day, hour), "MRN")
                .road(CN)
                .build());
    }

    /** Moved in the evening, arriving at Powers. */
    public static Event moved(EventLog log, String mark, int day, int tenths) {
        return moved(log, mark, day, tenths, "PWR");
    }

    public static Event moved(EventLog log, String mark, int day, int tenths, String station) {
        return log.record(Event.builder(EventKind.MOVED, CarMark.parse(mark), utc(2025, 1, day, 18), station)
                .tenths(tenths)
                .build());
    }

    /** Placed on the mill track at 20:00, on the usual waybill. */
    public static Event placed(EventLog log, String mark, int day) {
        return placed(log, mark, day, MILL_TRACK, WAYBILL);
    }

    public static Event placed(EventLog log, String mark, int day, String industry) {
        return placed(log, mark, day, industry, WAYBILL);
    }

    /** Placed on a track at its own station, with a waybill of its own or none when null. */
    public static Event placed(EventLog log, String mark, int day, String industry, Integer waybill) {
        return log.record(
                Event.builder(EventKind.PLACED, CarMark.parse(mark), utc(2025, 1, day, 20), stationOf(industry))
                        .industry(industry)
                        .waybill(waybill)
                        .build());
    }

    /** Released by the shipper at 21:00. */
    public static Event released(EventLog log, String mark, int day) {
        return released(log, mark, day, MILL_TRACK);
    }

    public static Event released(EventLog log, String mark, int day, String industry) {
        return log.record(
                Event.builder(EventKind.RELEASED, CarMark.parse(mark), utc(2025, 1, day, 21), stationOf(industry))
                        .industry(industry)
                        .build());
    }

    /** Pulled off the track at 22:00. */
    public static Event pulled(EventLog log, String mark, int day) {
        return pulled(log, mark, day, MILL_TRACK);
    }

    public static Event pulled(EventLog log, String mark, int day, String industry) {
        return log.record(
                Event.builder(EventKind.PULLED, CarMark.parse(mark), utc(2025, 1, day, 22), stationOf(industry))
                        .industry(industry)
                        .build());
    }

    /** Set out bad order at Escanaba at 08:00. */
    public static Event badOrdered(EventLog log, String mark, int day) {
        return badOrdered(log, mark, day, "ESC", SHOP_HOUR);
    }

    public static Event badOrdered(EventLog log, String mark, int day, String station) {
        return badOrdered(log, mark, day, station, SHOP_HOUR);
    }

    public static Event badOrdered(EventLog log, String mark, int day, String station, int hour) {
        return simple(log, EventKind.BAD_ORDERED, mark, day, station, hour);
    }

    /** Released from repair at Escanaba at 08:00. */
    public static Event repaired(EventLog log, String mark, int day) {
        return repaired(log, mark, day, "ESC", SHOP_HOUR);
    }

    public static Event repaired(EventLog log, String mark, int day, String station) {
        return repaired(log, mark, day, station, SHOP_HOUR);
    }

    public static Event repaired(EventLog log, String mark, int day, String station, int hour) {
        return simple(log, EventKind.REPAIRED, mark, day, station, hour);
    }

    /** Held under an embargo at Escanaba at 09:00. */
    public static Event held(EventLog log, String mark, int day) {
        return held(log, mark, day, "ESC", HOLD_HOUR);
    }

    public static Event held(EventLog log, String mark, int day, String station) {
        return held(log, mark, day, station, HOLD_HOUR);
    }

    public static Event held(EventLog log, String mark, int day, String station, int hour) {
        return simple(log, EventKind.HELD, mark, day, station, hour);
    }

    /** Released from a hold at Escanaba at 09:00. */
    public static Event freed(EventLog log, String mark, int day) {
        return freed(log, mark, day, "ESC", HOLD_HOUR);
    }

    public static Event freed(EventLog log, String mark, int day, String station) {
        return freed(log, mark, day, station, HOLD_HOUR);
    }

    public static Event freed(EventLog log, String mark, int day, String station, int hour) {
        return simple(log, EventKind.FREED, mark, day, station, hour);
    }

    private static Event simple(EventLog log, EventKind kind, String mark, int day, String station, int hour) {
        return log.record(Event.of(kind, CarMark.parse(mark), utc(2025, 1, day, hour), station));
    }

    /** The mill's tracks are at Escanaba and everybody else's at Powers. */
    private static String stationOf(String industry) {
        return industry.startsWith("ESC") ? "ESC" : "PWR";
    }
}
