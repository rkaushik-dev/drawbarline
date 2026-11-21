package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.CarRow;
import io.github.rkaushikdev.drawbarline.persistence.models.DefectRow;
import io.github.rkaushikdev.drawbarline.persistence.models.ObjectionRow;
import io.github.rkaushikdev.drawbarline.persistence.models.RepairCardRow;
import io.github.rkaushikdev.drawbarline.persistence.models.ResolutionRow;
import io.github.rkaushikdev.drawbarline.repairs.Defect;
import io.github.rkaushikdev.drawbarline.repairs.Ground;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.Objection;
import io.github.rkaushikdev.drawbarline.repairs.Outcome;
import io.github.rkaushikdev.drawbarline.repairs.RepairCard;
import io.github.rkaushikdev.drawbarline.repairs.Resolution;
import io.github.rkaushikdev.drawbarline.repairs.Responsibility;
import io.github.rkaushikdev.drawbarline.repairs.WhyMade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every repair card and every exception filed against one.
 *
 * <p>Cards go in and never change. Exceptions go in against a line and are answered once. Both
 * rules are enforced here as well as by the tables, because the message a clerk needs is that the
 * card is already on file, not that a unique constraint fired.
 *
 * <p>The ninety day filing clock is enforced here too, against the card on file rather than one
 * held in memory, so an exception filed late through this path is refused when it is filed.
 */
public final class CardRepository {

    private static final String BY_DAY = " order by c.performedOn, c.number";

    private final EntityManager session;

    public CardRepository(EntityManager session) {
        this.session = session;
    }

    static Defect defectFromRow(DefectRow row) {
        return new Defect(
                new JobCode(row.getJob()),
                new WhyMade(row.getWhyMade()),
                row.getQuantity(),
                row.getOverridden()
                        .filter(name -> !name.isEmpty())
                        .map(name -> Stored.named(Responsibility.class, name)),
                row.getPartYears(),
                row.getNote());
    }

    static RepairCard cardFromRow(RepairCardRow row) {
        return new RepairCard(
                row.getNumber(),
                row.getCarMark(),
                row.getPerformedBy(),
                row.getPerformedOn(),
                row.getStation(),
                row.getDefects().stream().map(CardRepository::defectFromRow).toList());
    }

    static RepairCardRow rowFromCard(RepairCard card) {
        RepairCardRow row = new RepairCardRow(card.number(), card.car(), card.performedBy(), card.on(), card.station());
        List<Defect> defects = card.defects();
        for (int at = 0; at < defects.size(); at++) {
            Defect one = defects.get(at);
            row.addDefect(new DefectRow(
                    at,
                    one.job().digits(),
                    one.why().digits(),
                    one.quantity(),
                    one.overridden().map(Responsibility::name).orElse(null),
                    one.partYears(),
                    one.note()));
        }
        return row;
    }

    static Objection objectionFromRow(ObjectionRow row) {
        return new Objection(
                row.getCardNumber(),
                row.getLine(),
                Stored.named(Ground.class, row.getGround()),
                row.getDisputed(),
                row.getFiledOn(),
                row.getNote());
    }

    /** File a card against a car on the register. A number already on file is refused. */
    public RepairCard file(RepairCard card) {
        if (session.find(RepairCardRow.class, card.number()) != null) {
            throw new BillingError("that card is already on file", Details.of("number", card.number()));
        }
        if (session.find(CarRow.class, CarRow.keyOf(card.car())) == null) {
            throw new UnknownCar(
                    "no record of that car", Details.of("car", card.car().toString()));
        }
        session.persist(rowFromCard(card));
        session.flush();
        return card;
    }

    public RepairCard get(int number) {
        RepairCardRow found = session.find(RepairCardRow.class, number);
        if (found == null) {
            throw new BillingError("no such card", Details.of("number", number));
        }
        return cardFromRow(found);
    }

    /** The cards written against one car, by day and then by number. */
    public List<RepairCard> forCar(CarMark car) {
        return cards(session.createQuery(
                        "select c from RepairCardRow c where c.carMark = :car" + BY_DAY, RepairCardRow.class)
                .setParameter("car", car));
    }

    /** The cards one road wrote, by number. */
    public List<RepairCard> byRoad(ReportingMark road) {
        return cards(session.createQuery(
                        "select c from RepairCardRow c where c.performedBy = :road order by c.number",
                        RepairCardRow.class)
                .setParameter("road", road));
    }

    /** The cards for work done from one day through another, both included. */
    public List<RepairCard> between(LocalDate first, LocalDate last) {
        return cards(session.createQuery(
                        "select c from RepairCardRow c where c.performedOn >= :first and c.performedOn <= :last"
                                + BY_DAY,
                        RepairCardRow.class)
                .setParameter("first", first)
                .setParameter("last", last));
    }

    private Optional<ObjectionRow> objectionRow(int cardNumber, int line) {
        return session.createQuery(
                        "select o from ObjectionRow o where o.cardNumber = :card and o.line = :line",
                        ObjectionRow.class)
                .setParameter("card", cardNumber)
                .setParameter("line", line)
                .getResultStream()
                .findFirst();
    }

    /**
     * File an exception against one line of a card on file, inside the filing clock. A line the card
     * has not got, or one already under exception, is refused.
     */
    public Objection takeException(Objection objection) {
        RepairCard card = get(objection.cardNumber());
        if (objection.line() >= card.defects().size()) {
            throw new BillingError(
                    "that card has no such line", Details.of("card", card.number(), "line", objection.line()));
        }
        LocalDate deadline = Objection.mustFileBy(card.on());
        if (objection.filedOn().isAfter(deadline)) {
            throw new BillingError(
                    "that exception is out of time", Details.of("card", card.number(), "by", deadline.toString()));
        }
        if (objectionRow(objection.cardNumber(), objection.line()).isPresent()) {
            throw new BillingError(
                    "that line is already under exception",
                    Details.of("card", objection.cardNumber(), "line", objection.line()));
        }
        session.persist(new ObjectionRow(
                objection.cardNumber(),
                objection.line(),
                objection.ground().name(),
                objection.disputed(),
                objection.filedOn(),
                objection.note()));
        session.flush();
        return objection;
    }

    /** Answer an exception on file. One already answered is refused. */
    public Resolution answer(Objection objection, Resolution resolution) {
        ObjectionRow found = objectionRow(objection.cardNumber(), objection.line())
                .orElseThrow(() -> new BillingError(
                        "no exception is on file for that line",
                        Details.of("card", objection.cardNumber(), "line", objection.line())));
        if (found.getResolution().isPresent()) {
            throw new BillingError(
                    "that exception is already settled",
                    Details.of("card", objection.cardNumber(), "line", objection.line()));
        }
        found.setResolution(new ResolutionRow(resolution.outcome().name(), resolution.allowed()));
        session.flush();
        return resolution;
    }

    /** The exceptions not yet answered, by card and then by line. */
    public List<Objection> openObjections() {
        return session
                .createQuery(
                        "select o from ObjectionRow o left join fetch o.resolution order by o.cardNumber, o.line",
                        ObjectionRow.class)
                .getResultList()
                .stream()
                .filter(row -> row.getResolution().isEmpty())
                .map(CardRepository::objectionFromRow)
                .toList();
    }

    /** How the exception against a line was answered, if one was filed and answered. */
    public Optional<Outcome> outcomeOf(int cardNumber, int line) {
        return objectionRow(cardNumber, line)
                .flatMap(ObjectionRow::getResolution)
                .map(answer -> Stored.named(Outcome.class, answer.getOutcome()));
    }

    /** How many cards are on file. */
    public int size() {
        return Counting.countOf(session, RepairCardRow.class);
    }

    private static List<RepairCard> cards(TypedQuery<RepairCardRow> query) {
        return query.getResultList().stream().map(CardRepository::cardFromRow).toList();
    }
}
