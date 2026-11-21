/**
 * Weights, kept in whole pounds.
 *
 * <p>Light weight, load limit and gross rail load are all stencilled in pounds, and the difference
 * between two of them decides whether a load is legal. A nominal capacity such as a hundred ton car
 * is not a weight but a class, and what it really names is a gross rail load, so the classes are an
 * enumeration with the real number attached.
 */
package io.github.rkaushikdev.drawbarline.weight;
