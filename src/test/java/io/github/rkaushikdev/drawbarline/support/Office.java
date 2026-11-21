package io.github.rkaushikdev.drawbarline.support;

import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.Numbering;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.repairs.Depreciation;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.LaborRate;
import io.github.rkaushikdev.drawbarline.repairs.PriceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The books as the office sets them up: a database with its schema and its counters open, and the
 * same database with the filings loaded and a few cars on the register.
 *
 * <p>Both are the suite's shared database, emptied and filled again, so each test has the books
 * to itself and can write to them freely.
 */
public final class Office {

    /** Where each document number series starts on a new set of books. */
    public static final Map<String, Integer> STARTING_NUMBERS = Map.of(
            Numbering.INVOICES, 5001,
            Numbering.STATEMENTS, 9001,
            Numbering.REPAIR_CARDS, 88001,
            Numbering.WAYBILLS, 40001);

    /** The cars on the loaded railroad's register. */
    public static final List<String> CARS = List.of("CN 400100", "CN 400101", "CN 400102", "DBL 104207");

    /** The day the railroad's filings take effect. */
    public static final LocalDate FILED = LocalDate.of(2024, 7, 1);

    private Office() {}

    /** A database with the schema and the counters and nothing else. */
    public static Database empty() {
        return TestDatabase.seeded(work -> STARTING_NUMBERS.forEach(work.numbering()::start));
    }

    /** A railroad with its filings loaded and a few cars on the register. */
    public static Database loaded() {
        Database books = empty();
        try (UnitOfWork work = books.unitOfWork()) {
            file(work);
            work.commit();
        }
        return books;
    }

    private static void file(UnitOfWork work) {
        for (Band band : Filings.someBands()) {
            work.rates().file(band);
        }
        work.customers().addShipper(Shippers.MILL);
        work.customers().addShipper(Shippers.ELEVATOR);
        work.customers().addIndustry(new Industry("ESCPAP1", "mill house track", "ESC", Shippers.MILL, 2));
        work.customers().addIndustry(new Industry("PWRGRN1", "elevator", "PWR", Shippers.ELEVATOR, 20));
        work.tariffs().publish(new Tariff("4200", FILED, 2, 1, List.of(new Step(2, 10000), new Step(0, 20000))));
        work.repairPricing().addPrice(new PriceLine(new JobCode("4312"), FILED, 250, 128000));
        work.repairPricing().addPrice(new PriceLine(new JobCode("5104"), FILED, 125, 84000));
        work.repairPricing().setDepreciation(new Depreciation("43", 4, 20));
        work.repairPricing().fileLabor(new LaborRate(Cars.CN, FILED, 4200));
        for (String mark : CARS) {
            work.fleet().add(Cars.aCar(mark));
        }
    }
}
