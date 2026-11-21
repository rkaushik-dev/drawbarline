package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.demurrage.AverageAgreement;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.demurrage.TariffBook;
import io.github.rkaushikdev.drawbarline.persistence.models.AverageAgreementRow;
import io.github.rkaushikdev.drawbarline.persistence.models.TariffRow;
import io.github.rkaushikdev.drawbarline.persistence.models.TariffStepRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * Every published demurrage tariff and every signed average agreement.
 *
 * <p>Publishing a tariff writes the header and its steps together, and reading one back builds the
 * domain object, which is where the rule that only the last step runs to the end is enforced. A
 * tariff assembled by hand in the database with two open ended steps fails at the read, loudly,
 * rather than pricing a month wrongly and quietly.
 */
public final class TariffRepository {

    private final EntityManager session;

    public TariffRepository(EntityManager session) {
        this.session = session;
    }

    static Tariff tariffFromRow(TariffRow row) {
        return new Tariff(
                row.getCode(),
                row.getEffective(),
                row.getFreeDaysLoaded(),
                row.getFreeDaysEmpty(),
                row.getSteps().stream()
                        .map(one -> new Step(one.getThrough(), one.getCents()))
                        .toList());
    }

    static TariffRow rowFromTariff(Tariff tariff) {
        TariffRow row =
                new TariffRow(tariff.code(), tariff.effective(), tariff.freeDaysLoaded(), tariff.freeDaysEmpty());
        List<Step> steps = tariff.steps();
        for (int at = 0; at < steps.size(); at++) {
            Step step = steps.get(at);
            row.addStep(new TariffStepRow(at, step.through(), Stored.whole(step.cents(), "cents")));
        }
        return row;
    }

    static AverageAgreement agreementFromRow(AverageAgreementRow row) {
        return new AverageAgreement(row.getShipperCode(), row.getDailyCents(), row.getEffective(), row.getExpires());
    }

    /** Publish a tariff and its steps. A number already used, or a day already taken, is refused. */
    public Tariff publish(Tariff tariff) {
        if (session.find(TariffRow.class, tariff.code()) != null) {
            throw new TariffError("that tariff is already published", Details.of("code", tariff.code()));
        }
        boolean standing = !session.createQuery("select t.code from TariffRow t where t.effective = :day", String.class)
                .setParameter("day", tariff.effective())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (standing) {
            throw new TariffError(
                    "a tariff already takes effect that day",
                    Details.of("effective", tariff.effective().toString()));
        }
        session.persist(rowFromTariff(tariff));
        session.flush();
        return tariff;
    }

    public Tariff tariff(String code) {
        TariffRow found = session.find(TariffRow.class, code);
        if (found == null) {
            throw new TariffError("no such tariff", Details.of("code", code));
        }
        return tariffFromRow(found);
    }

    /** The latest tariff to have taken effect by a day. */
    public Tariff inEffectOn(LocalDate day) {
        return session.createQuery(
                        "select t from TariffRow t where t.effective <= :day order by t.effective desc",
                        TariffRow.class)
                .setParameter("day", day)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(TariffRepository::tariffFromRow)
                .orElseThrow(() -> new TariffError("no tariff was in effect", Details.of("on", day.toString())));
    }

    /** Every tariff, in the order they took effect, as the book the demurrage is priced from. */
    public TariffBook book() {
        TariffBook book = new TariffBook();
        session.createQuery("select t from TariffRow t order by t.effective", TariffRow.class)
                .getResultList()
                .forEach(row -> book.publish(tariffFromRow(row)));
        return book;
    }

    /** Sign an average agreement. The same shipper and day twice is refused. */
    public AverageAgreement sign(AverageAgreement agreement) {
        boolean standing = !session.createQuery(
                        "select a.id from AverageAgreementRow a where a.shipperCode = :shipper"
                                + " and a.effective = :effective",
                        Integer.class)
                .setParameter("shipper", agreement.shipper())
                .setParameter("effective", agreement.effective())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (standing) {
            throw new TariffError("that agreement is already signed", Details.of("shipper", agreement.shipper()));
        }
        session.persist(new AverageAgreementRow(
                agreement.shipper(),
                Stored.whole(agreement.dailyCents(), "daily_cents"),
                agreement.effective(),
                agreement.expires().orElse(null)));
        session.flush();
        return agreement;
    }

    /** The agreement in force for a shipper on a day. */
    public AverageAgreement agreementFor(String shipper, LocalDate day) {
        return session
                .createQuery(
                        "select a from AverageAgreementRow a where a.shipperCode = :shipper order by a.effective",
                        AverageAgreementRow.class)
                .setParameter("shipper", shipper)
                .getResultList()
                .stream()
                .map(TariffRepository::agreementFromRow)
                .filter(found -> found.covers(day))
                .findFirst()
                .orElseThrow(() -> new TariffError(
                        "no agreement was in force", Details.of("shipper", shipper, "on", day.toString())));
    }

    /** Every agreement, by shipper and then by the day it took effect. */
    public List<AverageAgreement> agreements() {
        return session
                .createQuery(
                        "select a from AverageAgreementRow a order by a.shipperCode, a.effective",
                        AverageAgreementRow.class)
                .getResultList()
                .stream()
                .map(TariffRepository::agreementFromRow)
                .toList();
    }
}
