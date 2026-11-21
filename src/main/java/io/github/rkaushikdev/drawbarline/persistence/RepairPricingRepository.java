package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.DepreciationRow;
import io.github.rkaushikdev.drawbarline.persistence.models.LaborRateRow;
import io.github.rkaushikdev.drawbarline.persistence.models.PriceLineRow;
import io.github.rkaushikdev.drawbarline.repairs.Depreciation;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.LaborRate;
import io.github.rkaushikdev.drawbarline.repairs.LaborRates;
import io.github.rkaushikdev.drawbarline.repairs.PriceLine;
import io.github.rkaushikdev.drawbarline.repairs.PriceMaster;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;

/**
 * The repair price master, the depreciation rules and the labor rates.
 *
 * <p>All three are small and read on every line of every card, so the whole master loads into the
 * domain objects rather than being queried job by job. A month of cards from three roads is a few
 * hundred lines and the master is a few dozen rows.
 *
 * <p>The depreciation rules are the one thing here that is updated in place rather than superseded
 * by a new row. They are not dated, because the office manual reissues them and the old
 * percentages are of no interest once a card has been priced.
 */
public final class RepairPricingRepository {

    private final EntityManager session;

    public RepairPricingRepository(EntityManager session) {
        this.session = session;
    }

    static PriceLine priceFromRow(PriceLineRow row) {
        return new PriceLine(
                new JobCode(row.getJob()), row.getEffective(), row.getLaborHundredths(), row.getMaterialCents());
    }

    static PriceLineRow rowFromPrice(PriceLine line) {
        return new PriceLineRow(
                line.job().digits(),
                line.effective(),
                Stored.whole(line.laborHundredths(), "labor_hundredths"),
                Stored.whole(line.materialCents(), "material_cents"));
    }

    /** Price a job from a date. The same job priced twice from one date is refused. */
    public PriceLine addPrice(PriceLine line) {
        boolean standing = !session.createQuery(
                        "select p.id from PriceLineRow p where p.job = :job and p.effective = :effective",
                        Integer.class)
                .setParameter("job", line.job().digits())
                .setParameter("effective", line.effective())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (standing) {
            throw new BillingError(
                    "that job is already priced from that date",
                    Details.of(
                            "job",
                            line.job().toString(),
                            "effective",
                            line.effective().toString()));
        }
        session.persist(rowFromPrice(line));
        session.flush();
        return line;
    }

    /** File the depreciation rule for a family of parts, replacing the one on file if there is one. */
    public Depreciation setDepreciation(Depreciation rule) {
        DepreciationRow found = session.find(DepreciationRow.class, rule.family());
        if (found == null) {
            session.persist(new DepreciationRow(rule.family(), rule.percentAYear(), rule.floorPercent()));
        } else {
            found.setPercentAYear(rule.percentAYear());
            found.setFloorPercent(rule.floorPercent());
        }
        session.flush();
        return rule;
    }

    /** File what a road charges an hour from a date. A second rate the same day is refused. */
    public LaborRate fileLabor(LaborRate rate) {
        boolean standing = !session.createQuery(
                        "select l.id from LaborRateRow l where l.road = :road and l.effective = :effective",
                        Integer.class)
                .setParameter("road", rate.road())
                .setParameter("effective", rate.effective())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (standing) {
            throw new BillingError(
                    "that road already filed a rate that day",
                    Details.of("road", rate.road().toString()));
        }
        session.persist(
                new LaborRateRow(rate.road(), rate.effective(), Stored.whole(rate.centsAnHour(), "cents_an_hour")));
        session.flush();
        return rate;
    }

    /** The whole price master with its depreciation rules. */
    public PriceMaster master() {
        PriceMaster found = new PriceMaster();
        session.createQuery("select p from PriceLineRow p order by p.job, p.effective", PriceLineRow.class)
                .getResultList()
                .forEach(row -> found.add(priceFromRow(row)));
        session.createQuery("select d from DepreciationRow d order by d.family", DepreciationRow.class)
                .getResultList()
                .forEach(rule -> found.depreciateFamily(
                        new Depreciation(rule.getFamily(), rule.getPercentAYear(), rule.getFloorPercent())));
        return found;
    }

    /** Every labor rate every road has filed. */
    public LaborRates laborRates() {
        LaborRates found = new LaborRates();
        session.createQuery("select l from LaborRateRow l order by l.road, l.effective", LaborRateRow.class)
                .getResultList()
                .forEach(row -> found.file(new LaborRate(row.getRoad(), row.getEffective(), row.getCentsAnHour())));
        return found;
    }

    /** The rate a road charged on a day. */
    public LaborRate rateFor(ReportingMark road, LocalDate on) {
        return laborRates().rateFor(road, on);
    }

    /** How many price lines are on file. */
    public int size() {
        return Counting.countOf(session, PriceLineRow.class);
    }
}
