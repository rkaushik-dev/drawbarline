package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.customers.CustomerRegister;
import io.github.rkaushikdev.drawbarline.demurrage.Detention;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The demurrage reports.
 *
 * <p>The detention report is the one that goes out with the invoice, and it has to show the free
 * cars as well as the charged ones. A customer who only sees the cars they are being billed for
 * has no way to judge whether they are doing badly, and the argument that follows takes longer
 * than printing the extra lines.
 *
 * <p>The shipper summary is what the traffic manager reads. One line a customer, how many cars,
 * how many chargeable days, and what it came to, sorted by the money because that is the order
 * anybody reads it in anyway.
 */
public final class DemurrageReport {

    private DemurrageReport() {}

    /** One line a car, free ones included, by track and then car. */
    public static List<String> detentionReport(List<Detention> detentions, Tariff tariff, String title) {
        Table table = new Table(List.of(
                new Column("car"),
                new Column("track"),
                new Column("placed"),
                new Column("counted to"),
                new Column("days", Align.RIGHT),
                new Column("charge", Align.RIGHT)));
        List<Detention> ordered = detentions.stream()
                .sorted(Comparator.comparing(Detention::industry)
                        .thenComparing(one -> one.car().toString()))
                .toList();
        for (Detention one : ordered) {
            table.add(
                    one.car().toString(),
                    one.industry(),
                    one.placedOn().toString(),
                    one.countedTo().toString(),
                    String.valueOf(one.chargeable()),
                    Render.money(one.charge(tariff)));
        }
        long charged = ordered.stream().filter(one -> one.chargeable() != 0).count();
        table.total(
                ordered.size() + " cars",
                charged + " charged",
                "",
                "",
                String.valueOf(ordered.stream().mapToInt(Detention::chargeable).sum()),
                Render.money(Money.total(
                        ordered.stream().map(one -> one.charge(tariff)).toList())));
        List<String> out = new ArrayList<>(Render.heading("demurrage detention", title));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    /** Cars, chargeable days and money for each shipper, in the order the shippers first appear. */
    public static Map<String, ShipperTotals> byShipper(
            List<Detention> detentions, Tariff tariff, CustomerRegister customers) {
        Map<String, ShipperTotals> found = new LinkedHashMap<>();
        for (Detention one : detentions) {
            String shipper = customers.industry(one.industry()).shipper().code();
            found.put(
                    shipper,
                    found.getOrDefault(shipper, ShipperTotals.NONE).plus(one.chargeable(), one.charge(tariff)));
        }
        return Collections.unmodifiableMap(found);
    }

    /** One line a shipper, biggest bill first and then by code. */
    public static List<String> shipperReport(
            List<Detention> detentions, Tariff tariff, CustomerRegister customers, String title) {
        Table table = new Table(List.of(
                new Column("shipper"),
                new Column("name"),
                new Column("cars", Align.RIGHT),
                new Column("days", Align.RIGHT),
                new Column("charge", Align.RIGHT)));
        List<Map.Entry<String, ShipperTotals>> ordered = byShipper(detentions, tariff, customers).entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, ShipperTotals> pair) ->
                                -pair.getValue().amount().cents())
                        .thenComparing(Map.Entry::getKey))
                .toList();
        for (Map.Entry<String, ShipperTotals> pair : ordered) {
            ShipperTotals totals = pair.getValue();
            table.add(
                    pair.getKey(),
                    customers.shipper(pair.getKey()).name(),
                    String.valueOf(totals.cars()),
                    String.valueOf(totals.days()),
                    Render.money(totals.amount()));
        }
        table.total(
                ordered.size() + " shippers",
                "",
                String.valueOf(ordered.stream()
                        .mapToInt(pair -> pair.getValue().cars())
                        .sum()),
                String.valueOf(ordered.stream()
                        .mapToInt(pair -> pair.getValue().days())
                        .sum()),
                Render.money(Money.total(
                        ordered.stream().map(pair -> pair.getValue().amount()).toList())));
        List<String> out = new ArrayList<>(Render.heading("demurrage by shipper", title));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    /** How many cars came back inside free time and how many did not. */
    public static FreeAndCharged freeAndCharged(List<Detention> detentions) {
        int charged =
                (int) detentions.stream().filter(one -> one.chargeable() != 0).count();
        return new FreeAndCharged(detentions.size() - charged, charged);
    }
}
