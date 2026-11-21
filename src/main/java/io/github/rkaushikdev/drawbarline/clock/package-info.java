/**
 * The settlement clock.
 *
 * <p>Inside the system a moment is an instant in UTC and nothing else, because two roads on either
 * side of a time zone boundary have to agree on the order of events. At the edges a moment is local
 * time at a station, because that is what the clerk wrote down and what the tariff means by a day.
 *
 * <p>Between the two sits the awkward hour twice a year. A local time that does not exist on the
 * spring morning is refused outright, and one that happens twice on the autumn morning is refused
 * unless the caller says which of the two it was. Guessing silently is how an hour of car hire goes
 * missing every November.
 */
package io.github.rkaushikdev.drawbarline.clock;
