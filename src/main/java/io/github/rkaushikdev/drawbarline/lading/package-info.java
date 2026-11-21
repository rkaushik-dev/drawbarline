/**
 * What is in the car.
 *
 * <p>Freight is named by a standard transportation commodity code, seven digits narrowing from a
 * two digit major group to the particular product. Hazardous material carries its own major group,
 * which is why a code beginning 49 changes how the car is handled. A lading is a commodity and a
 * weight, and an empty car has no lading at all rather than a lading of nothing, because an empty
 * moves under a different rate, a different routing rule and a different demurrage clock.
 */
package io.github.rkaushikdev.drawbarline.lading;
