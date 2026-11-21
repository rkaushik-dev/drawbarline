/**
 * Money, kept in whole cents.
 *
 * <p>Money here is signed, because a settlement between two roads runs both ways in the same month
 * and the netting depends on one side of it being negative. Splitting never loses a cent: an
 * allocation hands out the whole amount every time, with the odd cents going to the largest
 * remainders, which is the rule both sides of a shared charge can reproduce.
 */
package io.github.rkaushikdev.drawbarline.money;
