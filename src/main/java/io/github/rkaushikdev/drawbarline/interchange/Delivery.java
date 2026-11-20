package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.agreements.Agreements;
import io.github.rkaushikdev.drawbarline.agreements.InterchangeAgreement;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.embargo.Embargo;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoRegister;
import io.github.rkaushikdev.drawbarline.embargo.Movement;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.Acceptance;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.fleet.Finding;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.network.Station;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether a car can be handed across, taking everything into account.
 *
 * <p>Three separate things can stop a car at a junction. The equipment inspection says whether the
 * car is fit. The embargo register says whether anything is stopped. The interchange agreement says
 * whether that road takes that kind of car at that place at that hour. A crew standing at the
 * junction wants one answer, so this asks all three and gives back one list, in the order they would
 * be found: the paper on the car first, then what the railroad has stopped, then what the
 * connection will take.
 *
 * <p>The daily cap needs a count, and the count comes off the event log rather than being carried
 * anywhere, because the cars already delivered today is exactly the sort of number that goes stale
 * in a column.
 */
public final class Delivery {

    private Delivery() {}

    /** How many cars have gone across at one junction to one road on a local day at its station. */
    public static int deliveredToday(
            EventLog log, String junction, ReportingMark road, LocalDate day, Station station) {
        int count = 0;
        for (Event event : log.atStation(junction)) {
            if (event.kind() == EventKind.DELIVERED
                    && event.road().filter(road::equals).isPresent()
                    && Clock.toLocal(station, event.when()).toLocalDate().equals(day)) {
                count++;
            }
        }
        return count;
    }

    /** Everything that stops an empty or a car of ordinary lading, in the order somebody would find it. */
    public static Verdict canDeliver(
            CarMark car,
            FleetRegister fleet,
            EventLog log,
            EmbargoRegister embargoes,
            InterchangeAgreement agreement,
            Station station,
            LocalDateTime local) {
        return canDeliver(car, fleet, log, embargoes, agreement, station, local, Optional.empty());
    }

    /**
     * Everything that stops the car, in the order somebody would find it.
     *
     * @param local the reading on the clock at the junction when the car is offered
     * @param commodity what the car carries, when it carries anything
     */
    public static Verdict canDeliver(
            CarMark car,
            FleetRegister fleet,
            EventLog log,
            EmbargoRegister embargoes,
            InterchangeAgreement agreement,
            Station station,
            LocalDateTime local,
            Optional<Commodity> commodity) {
        LocalDate day = local.toLocalDate();
        List<String> findings = new ArrayList<>();

        for (Finding one : Acceptance.inspect(fleet, car, day)) {
            findings.add(one.toString());
        }

        Movement movement = Movement.anywhere().atStation(agreement.junction()).byRoad(agreement.road());
        if (commodity.isPresent()) {
            movement = movement.carrying(commodity.get());
        }
        for (Embargo one : embargoes.against(day, movement)) {
            findings.add(one + " is in force");
        }

        int already = deliveredToday(log, agreement.junction(), agreement.road(), day, station);
        if (fleet.has(car)) {
            CarKind kind = fleet.get(car).kind();
            boolean hazardous = commodity.map(Commodity::hazardous).orElse(false);
            findings.addAll(Agreements.checkDelivery(agreement, kind, local, hazardous, already));
        }

        return new Verdict(car, agreement.junction(), agreement.road(), findings);
    }
}
