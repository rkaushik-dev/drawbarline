package io.github.rkaushikdev.drawbarline.reports;

/**
 * How many cars came back inside free time and how many did not.
 *
 * @param free the cars with no chargeable day
 * @param charged the cars with at least one
 */
public record FreeAndCharged(int free, int charged) {}
