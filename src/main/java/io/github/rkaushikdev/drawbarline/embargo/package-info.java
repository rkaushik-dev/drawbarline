/**
 * Embargoes, which are how a railroad says stop.
 *
 * <p>An embargo names what it stops, a station, a commodity family, a connection or a combination
 * of them, and runs from a date to a date. Cars caught by one do not move, and the hours they stand
 * are reclaimable. An embargo that restricts nothing is refused, because leaving every field empty
 * would embargo the entire railroad. A permit is the way around one, where the embargo allows
 * permits at all.
 */
package io.github.rkaushikdev.drawbarline.embargo;
