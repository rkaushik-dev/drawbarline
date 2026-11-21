/**
 * Where the stations sit, and how far it is between two of them.
 *
 * <p>The mileage part of car hire is the chargeable miles a car actually moved on our rail. Every
 * station has a milepost on one of the road's two lines, held in tenths of a mile, and a distance
 * is the difference between two of them. The branch leaves the main at Powers and there is no other
 * connection, so a move off the branch is measured to Powers and then along the main. That is the
 * shape of this railroad, not a routing algorithm.
 */
package io.github.rkaushikdev.drawbarline.mileage;
