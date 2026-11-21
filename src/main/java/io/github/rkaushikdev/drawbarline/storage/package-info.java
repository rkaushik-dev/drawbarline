/**
 * Storage, which is what demurrage turns into when nobody is coming.
 *
 * <p>After a stated number of chargeable days a car comes off the demurrage schedule and onto a
 * flat storage rate. Storage counts every day rather than only chargeable ones, because a stored
 * car occupies the track on a Sunday exactly as much as on a Tuesday. The conversion day itself
 * belongs to demurrage, and storage is billed from the day after.
 */
package io.github.rkaushikdev.drawbarline.storage;
