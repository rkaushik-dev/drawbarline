package io.github.rkaushikdev.drawbarline.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.events.CarState;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.Replay;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event routes.
 *
 * <p>A moment arrives either as a local clock reading at a station, which is what a clerk has, or
 * as an instant, which is what another system has. Both are accepted and exactly one of them is
 * required, because a body carrying both is a body where the two disagree and nobody notices.
 *
 * <p>The state route replays the stream rather than reading a status column, so a correction filed
 * against last Tuesday changes the answer immediately and there is nothing to rebuild.
 */
@RestController
@RequestMapping("/events")
@Tag(name = "events")
final class EventsController {

    private final Work work;
    private final Validator validator;

    EventsController(Work work, Validator validator) {
        this.work = work;
        this.validator = validator;
    }

    /** One event as it is recorded. */
    record EventIn(
            @NotNull String car,
            @NotNull String kind,
            @NotNull String station,
            @JsonDeserialize(using = Moments.Reading.class) Temporal local,
            @JsonDeserialize(using = Moments.Reading.class) Temporal moment,
            Boolean secondPass,
            String road,
            String industry,
            Integer waybill,
            Integer tenths,
            String note) {

        EventIn {
            secondPass = secondPass != null && secondPass;
            note = note == null ? "" : note;
        }

        /** A body malformed this way is a malformed body, not a railroading rule that was broken. */
        @JsonIgnore
        @AssertTrue(message = "Value error, give either a local reading or a moment, not both")
        boolean isOneClockOrTheOther() {
            return (local == null) != (moment == null);
        }
    }

    /** One event as it reads back, its moment in UTC. */
    record EventOut(
            String car,
            String kind,
            String station,
            Instant moment,
            Optional<String> road,
            Optional<String> industry,
            Optional<Integer> waybill,
            Optional<Integer> tenths,
            String note) {

        static EventOut of(Event event) {
            return new EventOut(
                    event.car().toString(),
                    event.kind().toString(),
                    event.station(),
                    event.when(),
                    event.road().map(ReportingMark::toString),
                    event.industry(),
                    event.waybill(),
                    event.tenths(),
                    event.note());
        }
    }

    /** What a car is doing, replayed from its stream. */
    record StateOut(
            String car,
            boolean onLine,
            Optional<String> station,
            Optional<String> industry,
            Optional<Integer> waybill,
            boolean badOrdered,
            boolean held,
            boolean movable,
            int tenthsRun,
            int events) {

        static StateOut of(CarState state) {
            return new StateOut(
                    state.car().toString(),
                    state.onLine(),
                    state.station(),
                    state.industry(),
                    state.waybill(),
                    state.badOrdered(),
                    state.held(),
                    state.movable(),
                    state.tenthsRun(),
                    state.applied());
        }
    }

    static Event eventOf(EventIn filing) {
        String station = Text.upper(Text.strip(filing.station()));
        Instant when;
        if (filing.local() != null) {
            if (!(filing.local() instanceof LocalDateTime local)) {
                throw new EventError("a local reading carries no zone", Details.of("station", station));
            }
            when = Clock.toUtc(Reference.drawbarLine().station(station), local, filing.secondPass());
        } else if (filing.moment() != null) {
            when = Clock.requireAware(filing.moment(), "moment");
        } else {
            throw new EventError("no clock reading and no moment", Details.of("car", filing.car()));
        }
        boolean named = filing.road() != null && !filing.road().isEmpty();
        boolean spotted = filing.industry() != null && !filing.industry().isEmpty();
        return Event.builder(EventKind.kindNamed(filing.kind()), CarMark.parse(filing.car()), when, station)
                .road(named ? ReportingMark.parse(filing.road()) : null)
                .industry(spotted ? Text.upper(Text.strip(filing.industry())) : null)
                .waybill(filing.waybill())
                .tenths(filing.tenths())
                .note(filing.note())
                .build();
    }

    /** Record one event against one car. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventOut record(@Valid @RequestBody EventIn filing) {
        return work.apply(books -> EventOut.of(books.events().record(eventOf(filing))));
    }

    /** A day of interchange in one transaction, all of it or none of it. */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Integer> recordBatch(@RequestBody List<EventIn> filings) {
        check(filings);
        return work.apply(books -> {
            for (EventIn filing : filings) {
                books.events().record(eventOf(filing));
            }
            return Map.of("recorded", filings.size());
        });
    }

    /** One car's stream, in the order it happened. */
    @GetMapping("/cars/{mark}")
    List<EventOut> streamOf(@PathVariable("mark") String mark) {
        return work.read(books -> {
            List<EventOut> found = new ArrayList<>();
            books.events().stream(CarMark.parse(mark)).forEach(event -> found.add(EventOut.of(event)));
            return found;
        });
    }

    /** What the car is doing, replayed from its stream. */
    @GetMapping("/cars/{mark}/state")
    StateOut stateOf(@PathVariable("mark") String mark) {
        return work.read(books -> StateOut.of(Replay.replay(books.events().stream(CarMark.parse(mark)))));
    }

    /** Every line of a batch held to the same rules as a single event, located by its position. */
    private void check(List<EventIn> filings) {
        List<Problem> problems = new ArrayList<>();
        for (int at = 0; at < filings.size(); at++) {
            if (filings.get(at) == null) {
                problems.add(new Problem(
                        "model_attributes_type",
                        List.of("body", at),
                        "Input should be a valid dictionary or object to extract fields from"));
                continue;
            }
            List<Problem> line = new ArrayList<>();
            for (ConstraintViolation<EventIn> violation : validator.validate(filings.get(at))) {
                line.add(Errors.problem(List.of("body", at), violation));
            }
            problems.addAll(Errors.inDeclarationOrder(line, 2, EventIn.class));
        }
        if (!problems.isEmpty()) {
            throw new RequestRejected(problems);
        }
    }
}
