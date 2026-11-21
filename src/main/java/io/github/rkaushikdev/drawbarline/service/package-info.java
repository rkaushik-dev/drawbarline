/**
 * The application layer.
 *
 * <p>Everything below works on objects and knows nothing about a database. The command line and the
 * HTTP interface above know about a database and nothing about how car hire is worked out. This is
 * the one place the two meet, and it is deliberately thin: load the registers, run what the domain
 * already knows how to do, write back what came out of it.
 *
 * <p>Every decision a person makes goes through here and writes an audit entry as it goes. A close
 * runs against a ledger loaded into memory and writes its postings only once it has all of them, so
 * a close that fails half way leaves the month exactly as it was.
 */
package io.github.rkaushikdev.drawbarline.service;
