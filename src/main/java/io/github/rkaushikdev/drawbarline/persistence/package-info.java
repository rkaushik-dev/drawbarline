/**
 * Storing the books, and reading them back as the same objects.
 *
 * <p>The database, its migrations and the unit of work every command and request opens; the
 * repositories, which are the only place a session and the domain meet; the mappers, which turn
 * rows into domain objects and back with both directions side by side, because the round trip is
 * the test that matters; and the numbering of documents. Nothing in the domain knows a database
 * exists.
 */
package io.github.rkaushikdev.drawbarline.persistence;
