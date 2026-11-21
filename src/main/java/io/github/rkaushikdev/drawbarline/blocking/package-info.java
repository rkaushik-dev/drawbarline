/**
 * Which block a car goes in, and the order the blocks stand.
 *
 * <p>A train is made of blocks, each the cars for one place or one connection, standing in the
 * order they will be set off. A blocking plan is per originating station and it is a filing rather
 * than a computation, so the plan is data and this package only applies it.
 *
 * <p>A car whose destination is in no block comes back as unblocked for somebody to look at. It is
 * never put on the end, because the end is somebody else's block and it would travel to the wrong
 * place.
 */
package io.github.rkaushikdev.drawbarline.blocking;
