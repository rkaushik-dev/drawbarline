package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The codes a repair is billed under.
 *
 * <p>Three of them ride on every line of a repair card. The job code says what was done. The why
 * made code says why it was done, and it is the one that decides who pays. The responsibility says
 * who that turned out to be: it usually falls out of the why made code but not always, which is why
 * an inspector may override it with a reason on file.
 *
 * <p>The table is this road's extract of the office manual, kept as data because the manual is
 * reissued every year and the codes move.
 */
public final class Codes {

    /** Every published why made code, what it means, and who normally answers for it. */
    public static final Map<String, WhyMadeMeaning> WHY_MADE = whyMade();

    private Codes() {}

    private static Map<String, WhyMadeMeaning> whyMade() {
        Map<String, WhyMadeMeaning> found = new LinkedHashMap<>();
        found.put("01", new WhyMadeMeaning("worn beyond limit", Responsibility.OWNER));
        found.put("02", new WhyMadeMeaning("broken in service", Responsibility.OWNER));
        found.put("03", new WhyMadeMeaning("damaged in handling", Responsibility.HANDLING_LINE));
        found.put("04", new WhyMadeMeaning("damaged by lading", Responsibility.THIRD_PARTY));
        found.put("05", new WhyMadeMeaning("missing", Responsibility.HANDLING_LINE));
        found.put("06", new WhyMadeMeaning("loose or shifted", Responsibility.OWNER));
        found.put("07", new WhyMadeMeaning("bent", Responsibility.HANDLING_LINE));
        found.put("08", new WhyMadeMeaning("cracked", Responsibility.OWNER));
        found.put("09", new WhyMadeMeaning("burned", Responsibility.THIRD_PARTY));
        found.put("10", new WhyMadeMeaning("derailment damage", Responsibility.HANDLING_LINE));
        found.put("11", new WhyMadeMeaning("applied to bring to standard", Responsibility.OWNER));
        found.put("12", new WhyMadeMeaning("condemned by rule", Responsibility.OWNER));
        found.put("13", new WhyMadeMeaning("vandalised", Responsibility.THIRD_PARTY));
        found.put("14", new WhyMadeMeaning("wrong part applied earlier", Responsibility.JOINT));
        return Collections.unmodifiableMap(found);
    }

    /** Read a why made code, taking surrounding space off. */
    public static WhyMade whyMade(String digits) {
        return new WhyMade(Text.strip(digits));
    }

    /** Every why made code that normally lands on one party, in code order. */
    public static List<String> codesAnsweredBy(Responsibility who) {
        return WHY_MADE.entrySet().stream()
                .filter(entry -> entry.getValue().usual() == who)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Who pays for a line, from its why made code. */
    public static Responsibility responsibilityFor(WhyMade code) {
        return code.usualResponsibility();
    }

    /** Who pays for a line, allowing an inspector to say otherwise with a reason on file. */
    public static Responsibility responsibilityFor(WhyMade code, Optional<Responsibility> overridden) {
        return overridden.orElseGet(code::usualResponsibility);
    }
}
