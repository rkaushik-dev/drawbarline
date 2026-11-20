package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.marks.CarMark;

/**
 * What makes two records the same handoff, which is everything but the clock: the car, the kind of
 * event by name, the station, and the UTC date it happened on.
 */
public record HandoffKey(CarMark car, String kind, String station, String day) {}
