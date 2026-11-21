/**
 * The HTTP interface over the books.
 *
 * <p>One controller a resource: the fleet, the event log, settlement and the close, the reports,
 * repair billing, the yard's operating routes, the audit trail and car orders. A refusal about the
 * books goes out as its code, message and context, with the status its kind decides. A request that
 * is not the shape its route takes is answered as unprocessable, with a list of what was wrong with
 * it.
 */
package io.github.rkaushikdev.drawbarline.api;
