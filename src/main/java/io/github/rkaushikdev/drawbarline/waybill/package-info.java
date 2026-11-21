/**
 * The waybill, which is the paper the car moves on.
 *
 * <p>A waybill says which car, whose freight, from where to where, by what route, and what is in
 * it. The route is the sequence of our stations the car is authorised over, and the chargeable
 * mileage is measured along it, so a car that went another way needs a corrected waybill rather
 * than a corrected mileage. An empty car moves on a waybill too, with no lading on it.
 */
package io.github.rkaushikdev.drawbarline.waybill;
