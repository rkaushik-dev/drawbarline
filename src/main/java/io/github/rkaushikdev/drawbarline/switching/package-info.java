/**
 * Building the switch list for a station.
 *
 * <p>The local pulls the cars the customers have released before it spots the cars the yard has for
 * them, because a full track has room for one more only once the crew has taken one off. A car that
 * is bad ordered or held does not move, a track that is not switched today is not worked, and a
 * track that is genuinely full takes a constructive placement. Nothing here records anything: the
 * plan is a list, and what actually happened comes back as events.
 */
package io.github.rkaushikdev.drawbarline.switching;
