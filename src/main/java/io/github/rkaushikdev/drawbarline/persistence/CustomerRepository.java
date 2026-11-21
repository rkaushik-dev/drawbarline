package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.customers.CustomerError;
import io.github.rkaushikdev.drawbarline.customers.CustomerRegister;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.customers.Shipper;
import io.github.rkaushikdev.drawbarline.persistence.models.IndustryRow;
import io.github.rkaushikdev.drawbarline.persistence.models.ShipperRow;
import io.github.rkaushikdev.drawbarline.util.Text;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Shippers and their track.
 *
 * <p>The switching days go in and out through one pair of functions, so the comma separated column
 * is understood in one place. Every day is checked against the week on the way back, because a hand
 * edited row with a typo in it would otherwise become an industry that is never switched and cars
 * that never come off it.
 */
public final class CustomerRepository {

    private final EntityManager session;

    public CustomerRepository(EntityManager session) {
        this.session = session;
    }

    /** The switching days as the column holds them: in week order, separated by commas. */
    static String daysToText(Set<String> days) {
        return Industry.WEEKDAYS.stream().filter(days::contains).collect(Collectors.joining(","));
    }

    /** The switching days read back from the column, refusing anything that is not a day. */
    static Set<String> daysFromText(String text) {
        List<String> found = Arrays.stream(text.split(",", -1))
                .map(Text::strip)
                .filter(day -> !day.isEmpty())
                .toList();
        TreeSet<String> unknown = new TreeSet<>(found);
        unknown.removeAll(Industry.WEEKDAYS);
        if (!unknown.isEmpty()) {
            throw new ConfigurationError("no such day", Details.of("days", List.copyOf(unknown)));
        }
        return Set.copyOf(found);
    }

    static Shipper shipperFromRow(ShipperRow row) {
        return new Shipper(row.getCode(), row.getName(), row.isAverageAgreement());
    }

    static Industry industryFromRow(IndustryRow row) {
        return new Industry(
                row.getCode(),
                row.getName(),
                row.getStation(),
                shipperFromRow(row.getShipper()),
                row.getSpots(),
                daysFromText(row.getSwitched()));
    }

    static IndustryRow rowFromIndustry(Industry industry) {
        return new IndustryRow(
                industry.code(),
                industry.name(),
                industry.station(),
                industry.spots(),
                daysToText(industry.switched()));
    }

    /** File a shipper. One already on the register is refused. */
    public Shipper addShipper(Shipper shipper) {
        if (session.find(ShipperRow.class, shipper.code()) != null) {
            throw new CustomerError("that shipper is already on the register", Details.of("code", shipper.code()));
        }
        session.persist(new ShipperRow(shipper.code(), shipper.name(), shipper.averageAgreement()));
        session.flush();
        return shipper;
    }

    /** File an industry under a shipper already on the register. */
    public Industry addIndustry(Industry industry) {
        if (session.find(IndustryRow.class, industry.code()) != null) {
            throw new CustomerError("that industry is already on the register", Details.of("code", industry.code()));
        }
        ShipperRow owner = session.find(ShipperRow.class, industry.shipper().code());
        if (owner == null) {
            throw new CustomerError(
                    "the shipper has to be on the register first",
                    Details.of(
                            "industry",
                            industry.code(),
                            "shipper",
                            industry.shipper().code()));
        }
        session.persist(owner.addIndustry(rowFromIndustry(industry)));
        session.flush();
        return industry;
    }

    public Shipper shipper(String code) {
        ShipperRow found = session.find(ShipperRow.class, code);
        if (found == null) {
            throw new CustomerError("no such shipper", Details.of("code", code));
        }
        return shipperFromRow(found);
    }

    public Industry industry(String code) {
        IndustryRow found = session.find(IndustryRow.class, code);
        if (found == null) {
            throw new CustomerError("no such industry", Details.of("code", code));
        }
        return industryFromRow(found);
    }

    /** The industries at one station, in code order. */
    public List<Industry> atStation(String station) {
        return session
                .createQuery(
                        "select i from IndustryRow i join fetch i.shipper where i.station = :station order by i.code",
                        IndustryRow.class)
                .setParameter("station", station)
                .getResultList()
                .stream()
                .map(CustomerRepository::industryFromRow)
                .toList();
    }

    /** Every shipper, in code order. */
    public List<Shipper> shippers() {
        return session
                .createQuery("select s from ShipperRow s order by s.code", ShipperRow.class)
                .getResultList()
                .stream()
                .map(CustomerRepository::shipperFromRow)
                .toList();
    }

    /** Build the register in memory of every shipper and every industry. */
    public CustomerRegister register() {
        CustomerRegister register = new CustomerRegister();
        shippers().forEach(register::addShipper);
        session.createQuery("select i from IndustryRow i join fetch i.shipper order by i.code", IndustryRow.class)
                .getResultList()
                .forEach(row -> register.addIndustry(industryFromRow(row)));
        return register;
    }

    /** How many industries are on the register. */
    public int size() {
        return Counting.countOf(session, IndustryRow.class);
    }
}
