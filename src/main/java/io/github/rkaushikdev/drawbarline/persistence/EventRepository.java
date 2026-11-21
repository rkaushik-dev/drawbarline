package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.persistence.models.CarRow;
import io.github.rkaushikdev.drawbarline.persistence.models.EventRow;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Every event, on the other side of a session.
 *
 * <p>Reading is by car and by window, because those are the two questions asked, and every read
 * comes back as domain events in the log's own order, so a caller cannot tell whether they came out
 * of the books or out of a test.
 *
 * <p>Writing refuses a duplicate before the database does, so the message says what happened rather
 * than naming a constraint. The constraint is still there, and it is what catches two clerks keying
 * the same sheet at the same moment. The same goes for an event against a car with no equipment
 * record: the foreign key would catch it, but tell the caller nothing.
 */
public final class EventRepository {

    private final EntityManager session;

    public EventRepository(EntityManager session) {
        this.session = session;
    }

    /** File one event against a car on the register. The same event twice is refused. */
    public Event record(Event event) {
        if (session.find(CarRow.class, CarRow.keyOf(event.car())) == null) {
            throw new UnknownCar(
                    "no record of that car", Details.of("car", event.car().toString()));
        }
        boolean standing = !session.createQuery(
                        "select e.id from EventRow e where e.carMark = :car and e.kind = :kind"
                                + " and e.happened = :when and e.station = :station",
                        Integer.class)
                .setParameter("car", event.car())
                .setParameter("kind", event.kind().name())
                .setParameter("when", event.when())
                .setParameter("station", event.station())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (standing) {
            throw new EventError("that event is already on the stream", Details.of("event", event.toString()));
        }
        session.persist(EventMappers.rowFromEvent(event));
        session.flush();
        return event;
    }

    /** File each event in turn, and say how many were filed. */
    public int recordAll(List<Event> events) {
        for (Event event : events) {
            record(event);
        }
        return events.size();
    }

    /** One car's events, as a stream. A car with nothing recorded has an empty one. */
    public CarStream stream(CarMark car) {
        CarStream stream = new CarStream(car);
        session.createQuery("select e from EventRow e where e.carMark = :car order by e.happened", EventRow.class)
                .setParameter("car", car)
                .getResultList()
                .forEach(row -> stream.record(EventMappers.eventFromRow(row)));
        return stream;
    }

    /** Every event inside a window: from its start, up to but not including its end. */
    public List<Event> during(Interval window) {
        return events(session.createQuery(
                        "select e from EventRow e where e.happened >= :start and e.happened < :end"
                                + " order by e.happened",
                        EventRow.class)
                .setParameter("start", window.start())
                .setParameter("end", window.end())
                .getResultList());
    }

    /** Every event of one kind, in the order they happened. */
    public List<Event> ofKind(EventKind kind) {
        return events(
                session.createQuery("select e from EventRow e where e.kind = :kind order by e.happened", EventRow.class)
                        .setParameter("kind", kind.name())
                        .getResultList());
    }

    /** Every car with at least one event, in mark order. */
    public List<CarMark> cars() {
        return session.createQuery("select distinct e.carMark from EventRow e order by e.carMark", CarMark.class)
                .getResultList();
    }

    /** The moment of the latest event, or nothing for an empty log. */
    public Optional<Instant> lastMoment() {
        return Optional.ofNullable(session.createQuery("select max(e.happened) from EventRow e", Instant.class)
                .getSingleResult());
    }

    /**
     * Build the log in memory that the close and the reports work against, in one query. A close
     * reads the whole log several times, and a query a car would be hundreds of round trips to
     * answer what one select answers.
     */
    public EventLog log() {
        EventLog found = new EventLog();
        session.createQuery("select e from EventRow e order by e.carMark, e.happened", EventRow.class)
                .getResultList()
                .forEach(row -> found.record(EventMappers.eventFromRow(row)));
        return found;
    }

    /** How many events are on file. */
    public int size() {
        return Counting.countOf(session, EventRow.class);
    }

    private static List<Event> events(List<EventRow> rows) {
        return rows.stream().map(EventMappers::eventFromRow).toList();
    }
}
