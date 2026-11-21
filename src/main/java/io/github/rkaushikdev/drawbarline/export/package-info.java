/**
 * Handing a month to somebody who is not this application.
 *
 * <p>A month goes out as one JSON document: the postings, the statements, the demurrage invoices
 * and the events behind them, with the figures as integers in the units the application holds
 * rather than as formatted money. Nothing is computed on the way out, so an export and the
 * statement that went out in the post carry the same numbers. The shape is versioned, because a
 * reader written for it will outlive whoever wrote it.
 */
package io.github.rkaushikdev.drawbarline.export;
