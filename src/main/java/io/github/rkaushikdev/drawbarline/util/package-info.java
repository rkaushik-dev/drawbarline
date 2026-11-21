/**
 * The text and number rules every message, sheet and report shares.
 *
 * <p>Character tests and trimming with one meaning of white space everywhere, values written as
 * literals in an error's context, and numbers written in fixed and general form. Each rule lives
 * here once, so two readers never disagree about a value and two reports never print the same
 * number two ways.
 */
package io.github.rkaushikdev.drawbarline.util;
