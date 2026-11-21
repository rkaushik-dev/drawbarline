package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.carhire.RateTable;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.persistence.models.RateBandRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * Every filed car hire band.
 *
 * <p>The table is small and it is read on every car of every month, so the whole schedule is loaded
 * into the domain table rather than queried band by band. A rate lookup happens once a piece of a
 * span, a span with a birthday and a rate change in it is four lookups, and there are hundreds of
 * cars.
 */
public final class RateRepository {

    private final EntityManager session;

    public RateRepository(EntityManager session) {
        this.session = session;
    }

    /** File a band. The same type, starting age and date twice is refused. */
    public Band file(Band band) {
        boolean standing = !session.createQuery(
                        "select b.id from RateBandRow b where b.typeCode = :type and b.fromAge = :age"
                                + " and b.effective = :effective",
                        Integer.class)
                .setParameter("type", band.typeCode())
                .setParameter("age", band.fromAge())
                .setParameter("effective", band.effective())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (standing) {
            throw new RateError(
                    "that band is already filed",
                    Details.of(
                            "type_code",
                            band.typeCode().toString(),
                            "effective",
                            band.effective().toString()));
        }
        session.persist(RateMappers.rowFromBand(band));
        session.flush();
        return band;
    }

    /** File each band in turn, and say how many were filed. */
    public int fileAll(List<Band> bands) {
        for (Band band : bands) {
            file(band);
        }
        return bands.size();
    }

    /** The bands for one type of car, by date and then by age. */
    public List<Band> bandsFor(CarTypeCode typeCode) {
        return session
                .createQuery(
                        "select b from RateBandRow b where b.typeCode = :type order by b.effective, b.fromAge",
                        RateBandRow.class)
                .setParameter("type", typeCode)
                .getResultList()
                .stream()
                .map(RateMappers::bandFromRow)
                .toList();
    }

    /** The days the rates for one type of car changed, once each and in order. */
    public List<LocalDate> effectiveDates(CarTypeCode typeCode) {
        return session.createQuery(
                        "select distinct b.effective from RateBandRow b where b.typeCode = :type order by b.effective",
                        LocalDate.class)
                .setParameter("type", typeCode)
                .getResultList();
    }

    /** Load the whole schedule, which is what a close works against. */
    public RateTable table() {
        RateTable table = new RateTable();
        session.createQuery("select b from RateBandRow b order by b.effective, b.fromAge", RateBandRow.class)
                .getResultList()
                .forEach(row -> table.add(RateMappers.bandFromRow(row)));
        return table;
    }

    /** How many bands are on file. */
    public int size() {
        return Counting.countOf(session, RateBandRow.class);
    }
}
