package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.carhire.RateTable;
import io.github.rkaushikdev.drawbarline.customers.CustomerRegister;
import io.github.rkaushikdev.drawbarline.demurrage.TariffBook;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.network.Network;
import java.util.Objects;

/**
 * Everything a month close reads, and the two registers it writes: the ledger and the register of
 * closed months.
 *
 * @param home the road whose books these are, which owes no car hire to itself
 */
public record Books(
        Network network,
        FleetRegister fleet,
        EventLog log,
        RateTable rates,
        TariffBook tariffs,
        CustomerRegister customers,
        Ledger ledger,
        PeriodRegister periods,
        ReportingMark home) {

    public Books {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(fleet, "fleet");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(rates, "rates");
        Objects.requireNonNull(tariffs, "tariffs");
        Objects.requireNonNull(customers, "customers");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(periods, "periods");
        Objects.requireNonNull(home, "home");
    }
}
