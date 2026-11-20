package io.github.rkaushikdev.drawbarline.train;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import io.github.rkaushikdev.drawbarline.reports.Align;
import io.github.rkaushikdev.drawbarline.reports.Column;
import io.github.rkaushikdev.drawbarline.reports.Render;
import io.github.rkaushikdev.drawbarline.reports.Table;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether a train can actually leave, and the wheel report the conductor carries.
 *
 * <p>The ruling grade south of Powers is light and the siding at Wallace is what governs the main,
 * which is why nothing longer than sixty cars goes south of Powers. The branch climbs to Iron
 * Mountain and takes half the tonnage.
 */
public final class Train {

    /** The slack between two drawbars, added at every coupling. */
    public static final Length SLACK_PER_COUPLING = new Length(6);

    /** The ratings of the main and the branch. */
    public static final List<District> DISTRICTS = List.of(
            new District(Mileage.MAIN, "escanaba to green bay", 6800, Length.of(4200)),
            new District(Mileage.BRANCH, "powers to niagara", 3400, Length.of(2600)));

    private Train() {}

    /** The rating for one line. */
    public static District district(String line) {
        for (District one : DISTRICTS) {
            if (one.line().equals(line)) {
                return one;
            }
        }
        throw new ConsistError("no such district", Details.of("line", line));
    }

    /** Every reason the train cannot leave as it stands. */
    public static List<String> check(Consist consist, District over) {
        List<String> found = new ArrayList<>();
        if (consist.tons() > over.maxTons()) {
            found.add(consist.tons() + " tons is over the " + over.maxTons() + " the " + over.name() + " takes");
        }
        if (consist.length().compareTo(over.maxLength()) > 0) {
            found.add(consist.length() + " is longer than the " + over.maxLength() + " siding allows");
        }
        return List.copyOf(found);
    }

    public static boolean clears(Consist consist, District over) {
        return check(consist, over).isEmpty();
    }

    /** The list the conductor carries, head end first, with the train totalled underneath. */
    public static List<String> wheelReport(Consist consist) {
        Table table = new Table(List.of(
                new Column("pos", Align.RIGHT),
                new Column("car"),
                new Column("lading"),
                new Column("tons", Align.RIGHT),
                new Column("length")));
        int at = 1;
        for (Position one : consist.positions()) {
            table.add(
                    String.valueOf(at++),
                    one.car().mark().toString(),
                    one.lading().map(lading -> lading.commodity().name()).orElse("empty"),
                    String.valueOf(Math.floorDiv(one.weight().pounds(), Weight.POUNDS_PER_TON)),
                    one.car().outsideLength().toString());
        }
        table.total(
                consist.cars() + " cars",
                consist.loads() + " loads " + consist.empties() + " empties",
                "",
                String.valueOf(consist.tons()),
                consist.length().toString());
        List<String> lines = new ArrayList<>(
                Render.heading("wheel report " + consist.symbol(), consist.day().toString()));
        lines.addAll(table.lines());
        return List.copyOf(lines);
    }
}
