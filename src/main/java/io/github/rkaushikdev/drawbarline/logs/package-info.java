/**
 * Logging, configured once and never as a side effect of loading a class.
 *
 * <p>Nothing touches the logging set up except {@code Logs.configure}, and only the command line
 * and the service call it. Every line carries the logger name, because the useful question when
 * something goes wrong at a close is which part of the close.
 */
package io.github.rkaushikdev.drawbarline.logs;
