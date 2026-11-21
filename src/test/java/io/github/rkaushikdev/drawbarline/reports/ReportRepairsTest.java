package io.github.rkaushikdev.drawbarline.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.repairs.Defect;
import io.github.rkaushikdev.drawbarline.repairs.Depreciation;
import io.github.rkaushikdev.drawbarline.repairs.Ground;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.LaborRate;
import io.github.rkaushikdev.drawbarline.repairs.LaborRates;
import io.github.rkaushikdev.drawbarline.repairs.Objection;
import io.github.rkaushikdev.drawbarline.repairs.PriceLine;
import io.github.rkaushikdev.drawbarline.repairs.PriceMaster;
import io.github.rkaushikdev.drawbarline.repairs.PricedCard;
import io.github.rkaushikdev.drawbarline.repairs.RepairCard;
import io.github.rkaushikdev.drawbarline.repairs.Responsibility;
import io.github.rkaushikdev.drawbarline.repairs.WhyMade;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportRepairsTest {

    static final JobCode WHEELSET = new JobCode("4312");
    static final JobCode COUPLER = new JobCode("5104");
    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");

    static PriceMaster aMaster() {
        PriceMaster master = new PriceMaster();
        master.add(new PriceLine(WHEELSET, LocalDate.of(2024, 7, 1), 250, 128000));
        master.add(new PriceLine(COUPLER, LocalDate.of(2024, 7, 1), 125, 84000));
        master.depreciateFamily(new Depreciation("43", 4, 20));
        return master;
    }

    static LaborRates someRates() {
        LaborRates rates = new LaborRates();
        rates.file(new LaborRate(CN, LocalDate.of(2024, 7, 1), 4200));
        rates.file(new LaborRate(ELS, LocalDate.of(2024, 7, 1), 3900));
        return rates;
    }

    static PricedCard aPriced() {
        return aPriced(88104, CN, "01");
    }

    /** A wheelset for the given reason and a coupler damaged in handling, on DBL 104207. */
    static PricedCard aPriced(int number, ReportingMark road, String why) {
        RepairCard card = new RepairCard(
                number,
                CarMark.parse("DBL 104207"),
                road,
                LocalDate.of(2025, 1, 14),
                "ESC",
                List.of(new Defect(WHEELSET, new WhyMade(why)), new Defect(COUPLER, new WhyMade("03"))));
        return PricedCard.price(card, aMaster(), someRates());
    }

    static List<PricedCard> someCards() {
        return List.of(aPriced(), aPriced(88105, ELS, "01"), aPriced(88106, CN, "01"));
    }

    static Objection anObjection(int card, int line, Ground ground, long cents) {
        return new Objection(card, line, ground, new Money(cents), LocalDate.of(2025, 2, 3));
    }

    static String firstStartingWith(List<String> lines, String prefix) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void cardsGatherByTheRoadThatWroteThem() {
        List<RoadTotals> found = RepairsReport.byRoad(someCards());
        assertThat(found.stream().map(one -> one.road().toString())).containsExactly("CN", "ELS");
        assertThat(found.get(0).cards()).isEqualTo(2);
    }

    @Test
    void theLinesAreCounted() {
        assertThat(RepairsReport.byRoad(someCards()).get(0).lines()).isEqualTo(4);
    }

    @Test
    void theSplitBetweenTheOwnerAndTheRoad() {
        RoadTotals found = RepairsReport.byRoad(List.of(aPriced())).get(0);
        assertThat(found.toOwner()).isEqualTo(new Money(138500));
        assertThat(found.borneByTheRoad()).isEqualTo(new Money(89250));
    }

    @Test
    void theTwoHalvesAddToWhatWasBilled() {
        RoadTotals found = RepairsReport.byRoad(someCards()).get(0);
        assertThat(found.toOwner().plus(found.borneByTheRoad())).isEqualTo(found.billed());
    }

    @Test
    void noCardsGatherToNothing() {
        assertThat(RepairsReport.byRoad(List.of())).isEmpty();
    }

    @Test
    void theBillingReportOpensWithAHeading() {
        List<String> lines = RepairsReport.billingReport(someCards(), "2025-01");
        assertThat(lines.get(0)).isEqualTo("REPAIR BILLING");
    }

    @Test
    void theBillingReportHasALineARoad() {
        List<String> lines = RepairsReport.billingReport(someCards(), "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("CN"));
        assertThat(lines).anyMatch(line -> line.startsWith("ELS"));
    }

    @Test
    void theBillingReportTotalsEveryRoad() {
        List<String> lines = RepairsReport.billingReport(someCards(), "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("2 roads"));
    }

    @Test
    void aMonthWithNoCardsStillTotals() {
        List<String> lines = RepairsReport.billingReport(List.of(), "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("0 roads"));
    }

    @Test
    void theResponsibilitySplitCoversEveryParty() {
        Map<Responsibility, Money> found = RepairsReport.responsibilitySplit(someCards());
        assertThat(found.keySet()).containsExactlyInAnyOrder(Responsibility.values());
    }

    @Test
    void wearLandsOnTheOwnerInTheSplit() {
        Map<Responsibility, Money> found = RepairsReport.responsibilitySplit(List.of(aPriced()));
        assertThat(found.get(Responsibility.OWNER)).isEqualTo(new Money(138500));
        assertThat(found.get(Responsibility.HANDLING_LINE)).isEqualTo(new Money(89250));
    }

    @Test
    void aCardOfLadingDamageLandsOnAThirdParty() {
        Map<Responsibility, Money> found = RepairsReport.responsibilitySplit(List.of(aPriced(88104, CN, "04")));
        assertThat(found.get(Responsibility.THIRD_PARTY)).isEqualTo(new Money(138500));
    }

    @Test
    void theExceptionReportListsWhatIsOpen() {
        List<Objection> objections = List.of(anObjection(88104, 0, Ground.DEPRECIATION, 20000));
        Map<Integer, PricedCard> cards = Map.of(88104, aPriced());
        List<String> lines = RepairsReport.exceptionReport(objections, cards, LocalDate.of(2025, 3, 1));
        assertThat(lines.get(0)).isEqualTo("OPEN REPAIR EXCEPTIONS");
        assertThat(lines).anyMatch(line -> line.startsWith("88104"));
    }

    @Test
    void theExceptionReportCountsTheDaysLeftToAnswer() {
        List<Objection> objections = List.of(anObjection(88104, 0, Ground.DEPRECIATION, 20000));
        List<String> lines =
                RepairsReport.exceptionReport(objections, Map.of(88104, aPriced()), LocalDate.of(2025, 3, 1));
        String[] words = Text.strip(firstStartingWith(lines, "88104")).split("\\s+");
        assertThat(words[words.length - 1]).isEqualTo("44");
    }

    @Test
    void theExceptionReportTotalsWhatIsDisputed() {
        List<Objection> objections =
                List.of(anObjection(88104, 0, Ground.DEPRECIATION, 20000), anObjection(88105, 1, Ground.PRICE, 5000));
        List<String> lines = RepairsReport.exceptionReport(objections, Map.of(), LocalDate.of(2025, 3, 1));
        assertThat(firstStartingWith(lines, "2 open")).contains("$250.00");
    }

    @Test
    void nothingOpenReportsNothing() {
        List<String> lines = RepairsReport.exceptionReport(List.of(), Map.of(), LocalDate.of(2025, 3, 1));
        assertThat(lines).anyMatch(line -> line.startsWith("0 open"));
    }

    @Test
    void noLineRunsPastThePaper() {
        for (String line : RepairsReport.billingReport(someCards(), "2025-01")) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }
}
