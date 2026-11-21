/**
 * Car orders, and which empties go against them.
 *
 * <p>Orders are filled oldest first, and inside a day lowest order number first. It is not the rule
 * that moves the fewest cars, it is the rule a shipper can check. Each order is filled with the
 * nearest suitable empty, measured along the track, and where two are the same distance the lower
 * car number wins, so two runs of the same morning distribute the same cars.
 */
package io.github.rkaushikdev.drawbarline.distribution;
