/**
 * Column converters that store what the domain actually means.
 *
 * <p>Money is held in cents, weights in pounds, lengths in inches, and marks and codes as small
 * value objects. A column read as a plain number would store the same bytes, but every read would
 * hand back a bare number and every call site would have to remember its unit. These take that
 * memory away: a read hands back the value object and a write takes one.
 */
package io.github.rkaushikdev.drawbarline.persistence.types;
