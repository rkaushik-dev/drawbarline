/**
 * Placarding, and where a placarded car may stand in a train.
 *
 * <p>A placarded car stands sixth from the engine or further back when the train is long enough to
 * put it there, and it is always separated from the engine, and from an occupied car at the rear,
 * by at least one car that is not placarded. The commodity decides the placard and the placard
 * decides the handling, and that chain runs one way only: nothing here reads a placard off a car and
 * infers what is in it. An empty car that last carried a hazardous commodity keeps its placards
 * until it is cleaned, so a residue movement is handled as though it were loaded.
 */
package io.github.rkaushikdev.drawbarline.hazmat;
