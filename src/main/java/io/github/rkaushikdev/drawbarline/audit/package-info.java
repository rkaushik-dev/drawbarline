/**
 * Who did what, and when they did it.
 *
 * <p>The event log is never edited and the ledger is never updated, so most of the application is
 * already an audit trail. What those lack is the person. This is a second, thinner log that records
 * only the things a person decides: closing a month, reopening one, retiring a car, lifting an
 * embargo, allowing an exception. It does not record reads, because a trail that records reads is a
 * trail nobody can find anything in.
 *
 * <p>The actor is a string checked against nothing. There is no user table on this railroad: it is
 * the initials on the bottom of the form, and the value of it is that somebody can be asked.
 */
package io.github.rkaushikdev.drawbarline.audit;
