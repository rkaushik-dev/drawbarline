package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.agreements.AgreementError;
import io.github.rkaushikdev.drawbarline.agreements.InterchangeAgreement;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.AgreementRow;
import io.github.rkaushikdev.drawbarline.util.Text;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every interchange agreement the road has signed.
 *
 * <p>The kind letters go in and out through one pair of functions, so the packed column is
 * understood in one place, and every letter is checked against the published kinds on the way back.
 * A hand edited row with a letter nobody publishes would otherwise become an agreement that silently
 * takes nothing.
 */
public final class AgreementRepository {

    private final EntityManager session;

    public AgreementRepository(EntityManager session) {
        this.session = session;
    }

    /** The kinds as the column holds them: their letters, in the order the kinds are published. */
    static String kindsToText(Set<CarKind> kinds) {
        return Arrays.stream(CarKind.values())
                .filter(kinds::contains)
                .map(CarKind::letter)
                .collect(Collectors.joining());
    }

    /** The kinds read back from the column, refusing a letter no kind carries. */
    static Set<CarKind> kindsFromText(String text) {
        return Text.strip(text)
                .codePoints()
                .mapToObj(Character::toString)
                .map(CarKind::fromLetter)
                .collect(Collectors.toUnmodifiableSet());
    }

    static InterchangeAgreement agreementFromRow(AgreementRow row) {
        return new InterchangeAgreement(
                row.getRoad(),
                row.getJunction(),
                row.getEffective(),
                kindsFromText(row.getKinds()),
                row.getDailyCap(),
                row.getOpens(),
                row.getCloses(),
                row.isHazardous(),
                row.getExpires());
    }

    static AgreementRow rowFromAgreement(InterchangeAgreement agreement) {
        return new AgreementRow(
                agreement.road(),
                agreement.junction(),
                agreement.effective(),
                kindsToText(agreement.kinds()),
                agreement.dailyCap(),
                agreement.opens(),
                agreement.closes(),
                agreement.hazardous(),
                agreement.expires().orElse(null));
    }

    /** Sign an agreement. The same road, junction and day twice is refused. */
    public InterchangeAgreement sign(InterchangeAgreement agreement) {
        boolean standing = !session.createQuery(
                        "select a.id from AgreementRow a where a.road = :road and a.junction = :junction"
                                + " and a.effective = :effective",
                        Integer.class)
                .setParameter("road", agreement.road())
                .setParameter("junction", agreement.junction())
                .setParameter("effective", agreement.effective())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (standing) {
            throw new AgreementError("that agreement is already signed", Details.of("junction", agreement.code()));
        }
        session.persist(rowFromAgreement(agreement));
        session.flush();
        return agreement;
    }

    /** The agreement in force at one junction with one road on a day: the latest that has not expired. */
    public InterchangeAgreement at(String junction, ReportingMark road, LocalDate on) {
        return session
                .createQuery(
                        "select a from AgreementRow a where a.junction = :junction and a.road = :road"
                                + " and a.effective <= :on order by a.effective desc",
                        AgreementRow.class)
                .setParameter("junction", junction)
                .setParameter("road", road)
                .setParameter("on", on)
                .getResultList()
                .stream()
                .map(AgreementRepository::agreementFromRow)
                .filter(found -> found.inForceOn(on))
                .findFirst()
                .orElseThrow(() -> new AgreementError(
                        "no agreement is in force there",
                        Details.of("junction", junction + "/" + road, "on", on.toString())));
    }

    /** The agreements in force on a day, by junction and then by road. */
    public List<InterchangeAgreement> inForceOn(LocalDate day) {
        return session
                .createQuery("select a from AgreementRow a order by a.junction, a.road", AgreementRow.class)
                .getResultList()
                .stream()
                .map(AgreementRepository::agreementFromRow)
                .filter(one -> one.inForceOn(day))
                .toList();
    }

    /** Every agreement, by junction, road and the day it took effect. */
    public List<InterchangeAgreement> all() {
        return session
                .createQuery(
                        "select a from AgreementRow a order by a.junction, a.road, a.effective", AgreementRow.class)
                .getResultList()
                .stream()
                .map(AgreementRepository::agreementFromRow)
                .toList();
    }

    /** How many agreements have been signed. */
    public int size() {
        return Counting.countOf(session, AgreementRow.class);
    }
}
