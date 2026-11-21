/**
 * Who ships, where their track is, and how often we get to it.
 *
 * <p>An industry is a piece of private track off ours, with a name, a spot capacity and a switching
 * schedule, and all three matter to demurrage. The capacity is what turns an actual placement into
 * a constructive one, and a car cannot be pulled on a day nobody switches the industry. Whether a
 * shipper is on an average agreement is a property of the shipper and not of the car, so it lives
 * here.
 */
package io.github.rkaushikdev.drawbarline.customers;
