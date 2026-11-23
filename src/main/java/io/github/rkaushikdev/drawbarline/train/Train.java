package io.github.rkaushikdev.drawbarline.train;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.hazmat.Hazmat;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import io.github.rkaushikdev.drawbarline.reports.WheelReport;
import io.github.rkaushikdev.drawbarline.weight.NominalCapacity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether a train can actually leave, and the wheel report the conductor carries.
 *
 * <p>The ruling grade south of Powers is light and the siding at Wallace is what governs the main,
 * which is why nothing longer than sixty cars goes south of Powers. The branch climbs to Iron
 * Mountain and takes half the tonnage, and its timber trestles are rated for a 263,000 pound car,
 * so a loaded 286,000 pound car the main takes without a thought is set out at Powers.
 */
public final class Train {

    /** The slack between two drawbars, added at every coupling. */
    public static final Length SLACK_PER_COUPLING = new Length(6);

    /** The ratings of the main and the branch. */
    public static final List<District> DISTRICTS = List.of(
            new District(
                    Mileage.MAIN,
                    "escanaba to green bay",
                    6800,
                    Length.of(4200),
                    NominalCapacity.ONE_TEN.grossRailLoad()),
            new District(
                    Mileage.BRANCH,
                    "powers to niagara",
                    3400,
                    Length.of(2600),
                    NominalCapacity.HUNDRED.grossRailLoad()));

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

    /**
     * Every reason the train cannot leave as it stands: the whole train first, then car by car,
     * then where the placarded cars stand.
     */
    public static List<String> check(Consist consist, District over) {
        List<String> found = new ArrayList<>();
        tonnage(consist, over).ifPresent(found::add);
        length(consist, over).ifPresent(found::add);
        found.addAll(tooHeavy(consist, over));
        found.addAll(Hazmat.checkStanding(consist.standings()));
        return List.copyOf(found);
    }

    private static Optional<String> tonnage(Consist consist, District over) {
        return consist.tons() > over.maxTons()
                ? Optional.of(consist.tons() + " tons is over the " + over.maxTons() + " the " + over.name() + " takes")
                : Optional.empty();
    }

    private static Optional<String> length(Consist consist, District over) {
        return consist.length().compareTo(over.maxLength()) > 0
                ? Optional.of(consist.length() + " is longer than the " + over.maxLength() + " siding allows")
                : Optional.empty();
    }

    /** Every car heavier on the rail than the district's bridges take, in train order. */
    private static List<String> tooHeavy(Consist consist, District over) {
        return consist.positions().stream()
                .filter(one -> !over.takesCar(one))
                .map(one -> one.car().mark() + " weighs " + one.weight() + " on the rail and the " + over.name()
                        + " takes " + over.maxCarWeight() + " a car")
                .toList();
    }

    /** The cars that have to come out before the train can run over a district, in train order. */
    public static List<Position> setOuts(Consist consist, District over) {
        return consist.positions().stream().filter(one -> !over.takesCar(one)).toList();
    }

    public static boolean clears(Consist consist, District over) {
        return check(consist, over).isEmpty();
    }

    /** The list the conductor carries, head end first, with the train totalled underneath. */
    public static List<String> wheelReport(Consist consist) {
        return WheelReport.of(consist);
    }
}
