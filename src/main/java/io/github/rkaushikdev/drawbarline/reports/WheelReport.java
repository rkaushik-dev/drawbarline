package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.train.Consist;
import io.github.rkaushikdev.drawbarline.train.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The wheel report the conductor carries: the train head end first, a line a car, with the train
 * totalled underneath.
 *
 * <p>The placard column is the one the crew reads first. It names the class and the number of what
 * is on the car's sides, residue empties included, so a crew setting out a car knows what it is
 * handling without going back to the waybills.
 */
public final class WheelReport {

    private WheelReport() {}

    /** The report for one train. */
    public static List<String> of(Consist consist) {
        Table table = new Table(List.of(
                new Column("pos", Align.RIGHT),
                new Column("car"),
                new Column("lading"),
                new Column("placard"),
                new Column("tons", Align.RIGHT),
                new Column("length")));
        int at = 1;
        for (Position one : consist.positions()) {
            table.add(
                    String.valueOf(at++),
                    one.car().mark().toString(),
                    one.lading().map(lading -> lading.commodity().name()).orElse("empty"),
                    placard(one),
                    String.valueOf(one.tons()),
                    one.car().outsideLength().toString());
        }
        long placarded = consist.positions().stream()
                .filter(one -> one.placard().isPresent())
                .count();
        table.total(
                consist.cars() + " cars",
                consist.loads() + " loads " + consist.empties() + " empties",
                "",
                placarded + " placarded",
                String.valueOf(consist.tons()),
                consist.length().toString());
        List<String> lines = new ArrayList<>(
                Render.heading("wheel report " + consist.symbol(), consist.day().toString()));
        lines.addAll(table.lines());
        return List.copyOf(lines);
    }

    /** {@code UN1824 class 8}, with {@code residue} on an empty, or nothing on an unplacarded car. */
    static String placard(Position one) {
        return one.placard()
                .map(placard -> String.format(
                                Locale.ROOT,
                                "UN%04d class %d",
                                placard.unNumber(),
                                placard.hazard().number())
                        + (one.loaded() ? "" : " residue"))
                .orElse("");
    }
}
