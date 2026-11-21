/**
 * What each connection will take, and when.
 *
 * <p>An interchange runs under an agreement that says which kinds of car that road will accept at
 * that junction, whether it will take hazardous material there at all, how many cars a day it will
 * take, and what hours anybody is there to take them. Handing a road a car outside its agreement is
 * not an error the system stops. It is a car standing at a junction overnight earning car hire, so
 * the daily cap is checked before the train is made up rather than after it arrives.
 *
 * <p>Manned hours are stated in local time at the junction, because that is what the agreement says
 * and because the junctions on this railroad are not all on the same clock.
 */
package io.github.rkaushikdev.drawbarline.agreements;
