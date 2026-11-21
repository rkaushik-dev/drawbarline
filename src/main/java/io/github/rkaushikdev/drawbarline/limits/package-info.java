/**
 * How long a field is allowed to be.
 *
 * <p>Each number appears once, here, and both the domain object that refuses a value too long and
 * the column that stores it read it from here, so the two cannot drift apart. None of them is a
 * rule about railroading: they are what fits on the form.
 */
package io.github.rkaushikdev.drawbarline.limits;
