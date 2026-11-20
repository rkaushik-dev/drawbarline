package io.github.rkaushikdev.drawbarline.support;

import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.carhire.Rate;
import io.github.rkaushikdev.drawbarline.carhire.RateTable;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.demurrage.TariffBook;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import java.time.LocalDate;
import java.util.List;

/** The car hire schedule and the demurrage tariff the test railroad runs on, as filings. */
public final class Filings {

    /** The covered hopper type the test fleet is made of. */
    public static final CarTypeCode HOPPER = new CarTypeCode("L351");

    private Filings() {}

    /** The car hire schedule this railroad runs on: a new car rate and an older car rate. */
    public static List<Band> someBands() {
        return List.of(
                new Band(HOPPER, 0, 9, LocalDate.of(2024, 7, 1), new Rate(4630, 1210)),
                new Band(HOPPER, 10, 99, LocalDate.of(2024, 7, 1), new Rate(3180, 1210)));
    }

    /** The schedule as a table. */
    public static RateTable aRateTable() {
        RateTable table = new RateTable();
        for (Band band : someBands()) {
            table.add(band);
        }
        return table;
    }

    /** Tariff 4200: two free days loaded, one empty, $100 a day for two days and $200 after. */
    public static TariffBook aTariffBook() {
        TariffBook book = new TariffBook();
        book.publish(
                new Tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000))));
        return book;
    }
}
