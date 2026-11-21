/**
 * Whether a set of books is ready to be worked.
 *
 * <p>A database that was built but never had its filings loaded closes a month that had traffic in
 * it and produces nothing, and no step of that is an error. So this asks the questions somebody
 * would ask before running a close, one at a time, and every finding says what to do about it.
 */
package io.github.rkaushikdev.drawbarline.health;
