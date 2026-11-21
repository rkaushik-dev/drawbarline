package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.distribution.Assignment;
import io.github.rkaushikdev.drawbarline.distribution.CarOrder;
import io.github.rkaushikdev.drawbarline.distribution.Distribution;
import io.github.rkaushikdev.drawbarline.distribution.Empty;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The distribution sheet.
 *
 * <p>Two halves, and the second one is the one that gets read. The first says which car goes to
 * which order, which is what the crews work off. The second says which orders could not be filled
 * and by how many, which is what the traffic manager has to telephone about before the shipper
 * does.
 *
 * <p>The empty mileage is on the bottom because it is the only number that says whether the
 * distribution was any good. Filling every order by dragging empties the length of the railroad
 * is a distribution that worked and cost a day.
 */
public final class DistributionReport {

    private DistributionReport() {}

    /** What went where, and what did not go at all. An order number nobody filed prints blank. */
    public static List<String> distributionReport(Distribution found, List<CarOrder> orders, String title) {
        Map<Integer, CarOrder> byNumber = new HashMap<>();
        for (CarOrder one : orders) {
            byNumber.put(one.number(), one);
        }
        Table table = new Table(List.of(
                new Column("order", Align.RIGHT),
                new Column("shipper"),
                new Column("car"),
                new Column("from"),
                new Column("to"),
                new Column("miles", Align.RIGHT)));
        for (Assignment one : found.assignments()) {
            Optional<CarOrder> order = Optional.ofNullable(byNumber.get(one.order()));
            table.add(
                    String.valueOf(one.order()),
                    order.map(CarOrder::shipper).orElse(""),
                    one.car().toString(),
                    one.fromStation(),
                    one.toStation(),
                    Render.miles(one.tenths()));
        }
        table.total(found.filled() + " cars", "", "", "", "", Render.miles(found.emptyTenths()));
        List<String> lines = new ArrayList<>(Render.heading("car distribution", title));
        lines.addAll(table.lines());
        lines.add("");

        if (found.isComplete()) {
            lines.add("every order filled");
            return List.copyOf(lines);
        }

        Table shortTable = new Table(List.of(
                new Column("order", Align.RIGHT),
                new Column("shipper"),
                new Column("track"),
                new Column("wanted"),
                new Column("short", Align.RIGHT)));
        List<Integer> numbers = found.shortages().keySet().stream().sorted().toList();
        for (int number : numbers) {
            Optional<CarOrder> order = Optional.ofNullable(byNumber.get(number));
            shortTable.add(
                    String.valueOf(number),
                    order.map(CarOrder::shipper).orElse(""),
                    order.map(CarOrder::industry).orElse(""),
                    order.map(one -> one.wantedOn().toString()).orElse(""),
                    String.valueOf(found.shortages().get(number)));
        }
        shortTable.total(found.shortages().size() + " orders", "", "", "", String.valueOf(found.unfilled()));
        lines.add("orders not filled");
        lines.addAll(shortTable.lines());
        return List.copyOf(lines);
    }

    /** Every car standing empty, by the station it is standing at and then the car. */
    public static List<String> emptiesReport(List<Empty> empties, String title) {
        Table table = new Table(List.of(new Column("station"), new Column("kind"), new Column("car")));
        List<Empty> ordered = empties.stream()
                .sorted(Comparator.comparing(Empty::station)
                        .thenComparing(one -> one.car().toString()))
                .toList();
        for (Empty one : ordered) {
            table.add(one.station(), one.kind().description(), one.car().toString());
        }
        table.total(empties.size() + " empties", "", "");
        List<String> out = new ArrayList<>(Render.heading("empties on hand", title));
        out.addAll(table.lines());
        return List.copyOf(out);
    }
}
