/**
 * Lengths, kept in whole inches.
 *
 * <p>Car dimensions come in feet and inches, never in decimal feet, and the inch matters because
 * clearance is decided on it. Holding a whole number of inches means no length is ever compared
 * with a tolerance. Nothing here is negative: a negative length is always a subtraction done in the
 * wrong order.
 */
package io.github.rkaushikdev.drawbarline.measure;
