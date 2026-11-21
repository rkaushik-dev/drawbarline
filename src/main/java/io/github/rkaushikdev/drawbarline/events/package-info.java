/**
 * What happened to a car, in the order it happened.
 *
 * <p>An event is the only way anything about a car changes. The register says what a car is, the
 * stream says where it has been, and everything billed is derived from the stream by replaying it.
 * Events are ordered by when they happened rather than by when the paperwork arrived.
 */
package io.github.rkaushikdev.drawbarline.events;
