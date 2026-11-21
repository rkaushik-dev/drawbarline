package io.github.rkaushikdev.drawbarline.support;

import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.settlement.Books;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.PeriodRegister;

/** The whole small working railroad as one set of books, for the tests that need every register. */
public final class Railroad {

    private Railroad() {}

    /** The network the way the road actually is, built fresh. */
    public static Network aNetwork() {
        return Reference.drawbarLine();
    }

    /**
     * Fresh books on the test railroad: a fleet of the usual hopper under each mark, an empty event
     * log, the filed car hire schedule and demurrage tariff, both shippers, an empty ledger with
     * every month open, and DBL as the home road.
     */
    public static Books books(String... marks) {
        return new Books(
                aNetwork(),
                Cars.aFleet(marks),
                new EventLog(),
                Filings.aRateTable(),
                Filings.aTariffBook(),
                Shippers.aCustomerRegister(),
                new Ledger(),
                new PeriodRegister(),
                Cars.HOME);
    }
}
