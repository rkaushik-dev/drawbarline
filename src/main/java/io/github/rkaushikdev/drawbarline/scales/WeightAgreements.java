package io.github.rkaushikdev.drawbarline.scales;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Every weight agreement on file and every check weigh taken against one.
 *
 * <p>An agreed weight is a promise the shipper keeps by loading the same way every day, and the
 * check weigh is how the road finds out whether they still do. A check weigh that comes out further
 * from the agreed weight than {@link #VARIANCE_TENTHS_PERCENT} suspends the agreement from that day:
 * every car after it bills on the scale until a new agreement is filed. A new agreement taking
 * effect after the check weigh is not touched by it, because it is the shipper's new promise.
 *
 * <p>Two agreements covering the same shipper's commodity on one day are a filing error. Filing one
 * is refused; a register read in from somewhere else keeps them and refuses to bill on them.
 */
public final class WeightAgreements {

    /** How far a check weigh may come out from the agreed weight before the agreement stops. */
    public static final int VARIANCE_TENTHS_PERCENT = 10;

    private final List<WeightAgreement> filed = new ArrayList<>();
    private final List<CheckWeigh> checks = new ArrayList<>();

    /** A register holding these agreements as they are, clashes and all. */
    public static WeightAgreements of(List<WeightAgreement> agreements) {
        WeightAgreements found = new WeightAgreements();
        found.filed.addAll(agreements);
        return found;
    }

    /** File an agreement, refusing one that clashes with an agreement already on file. */
    public WeightAgreement file(WeightAgreement agreement) {
        for (WeightAgreement standing : filed) {
            if (clash(standing, agreement)) {
                throw new ScaleError(
                        "that agreement overlaps one already on file",
                        Details.of(
                                "shipper",
                                agreement.shipper(),
                                "commodity",
                                agreement.commodity().code().toString(),
                                "standing",
                                standing.term().toString()));
            }
        }
        filed.add(agreement);
        return agreement;
    }

    /** Put a check weigh on file, handing back the agreement it suspended if it suspended one. */
    public Optional<WeightAgreement> checkWeigh(CheckWeigh check) {
        checks.add(check);
        checks.sort(Comparator.comparing(CheckWeigh::on));
        return filed.stream()
                .filter(one -> check.against(one) && breaks(check, one))
                .findFirst();
    }

    /** Whether a check weigh came out too far from an agreement to leave it standing. */
    public static boolean breaks(CheckWeigh check, WeightAgreement agreement) {
        return check.varianceTenthsPercent(agreement) > VARIANCE_TENTHS_PERCENT;
    }

    /** The check weigh that stopped an agreement by a day, if one did. */
    public Optional<CheckWeigh> suspendedBy(WeightAgreement agreement, LocalDate day) {
        return checks.stream()
                .filter(one -> !one.on().isAfter(day) && one.against(agreement) && breaks(one, agreement))
                .findFirst();
    }

    /** The one standing agreement that bills a shipper's commodity on a day, refusing two that both claim to. */
    public Optional<WeightAgreement> agreementFor(String shipper, Commodity commodity, LocalDate day) {
        List<WeightAgreement> applying = filed.stream()
                .filter(one -> one.appliesTo(shipper, commodity, day))
                .toList();
        if (applying.size() > 1) {
            throw new ScaleError(
                    "two weight agreements cover that commodity on that day",
                    Details.of("shipper", shipper, "commodity", commodity.code().toString(), "on", day.toString()));
        }
        return applying.stream().filter(one -> suspendedBy(one, day).isEmpty()).findFirst();
    }

    /** What a car bills at: the standing agreement if there is one, else the scale if it was weighed. */
    public Weight billingWeight(String shipper, Commodity commodity, LocalDate day, Optional<Weight> weighed) {
        Optional<WeightAgreement> agreed = agreementFor(shipper, commodity, day);
        if (agreed.isPresent()) {
            return agreed.get().agreed();
        }
        return weighed.orElseThrow(() -> new ScaleError(
                "no agreement and no weight, so nothing to bill on",
                Details.of("shipper", shipper, "commodity", commodity.code().toString())));
    }

    /** Whether two agreements are for the same shipper's commodity and share a day. */
    public static boolean clash(WeightAgreement one, WeightAgreement other) {
        return one.shipper().equals(other.shipper())
                && one.commodity().code().equals(other.commodity().code())
                && one.term().overlaps(other.term());
    }

    /** Every pair of agreements that clash, each pair once, in the order they were filed. */
    public List<List<WeightAgreement>> overlapping() {
        List<List<WeightAgreement>> found = new ArrayList<>();
        for (int at = 0; at < filed.size(); at++) {
            for (int later = at + 1; later < filed.size(); later++) {
                if (clash(filed.get(at), filed.get(later))) {
                    found.add(List.of(filed.get(at), filed.get(later)));
                }
            }
        }
        return List.copyOf(found);
    }

    /** Every agreement, in the order filed. */
    public List<WeightAgreement> agreements() {
        return List.copyOf(filed);
    }

    /** Every check weigh, oldest first. */
    public List<CheckWeigh> checkWeighs() {
        return List.copyOf(checks);
    }
}
