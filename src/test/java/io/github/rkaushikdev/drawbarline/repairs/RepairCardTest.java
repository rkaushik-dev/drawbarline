package io.github.rkaushikdev.drawbarline.repairs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepairCardTest {

    static final JobCode WHEELSET = new JobCode("4312");
    static final JobCode COUPLER = new JobCode("5104");
    static final CarMark CAR = CarMark.parse("DBL 104207");
    static final ReportingMark CN = new ReportingMark("CN");
    static final LocalDate DAY = LocalDate.of(2025, 1, 14);

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
        return rates;
    }

    static RepairCard card(int number, ReportingMark performedBy, String station, List<Defect> defects) {
        return new RepairCard(number, CAR, performedBy, DAY, station, defects);
    }

    static RepairCard aCard(List<Defect> defects) {
        return card(88104, CN, "ESC", defects);
    }

    static RepairCard aCard() {
        return aCard(List.of(new Defect(WHEELSET, new WhyMade("01"))));
    }

    static PricedCard priced(RepairCard card) {
        return PricedCard.price(card, aMaster(), someRates());
    }

    @Test
    void aCardPrintsItsNumberCarRoadAndDate() {
        assertThat(aCard()).hasToString("card 88104 DBL 104207 by CN on 2025-01-14");
    }

    @Test
    void aDefectPrintsTheCodesAndWhoAnswers() {
        assertThat(new Defect(WHEELSET, new WhyMade("01"))).hasToString("4312 why 01 x1 to owner");
    }

    @Test
    void aDefectOfNoPartsIsRefused() {
        assertThatThrownBy(() -> new Defect(WHEELSET, new WhyMade("01")).withQuantity(0))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void aPartOfNegativeAgeIsRefused() {
        assertThatThrownBy(() -> new Defect(WHEELSET, new WhyMade("01")).withPartYears(-1))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void responsibilityFollowsTheWhyMadeCode() {
        assertThat(new Defect(WHEELSET, new WhyMade("03")).answeredBy()).isSameAs(Responsibility.HANDLING_LINE);
    }

    @Test
    void anOverrideNeedsAReason() {
        assertThatThrownBy(() -> new Defect(WHEELSET, new WhyMade("01")).withOverridden(Responsibility.HANDLING_LINE))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void anOverrideWithAReasonStands() {
        Defect defect = new Defect(WHEELSET, new WhyMade("01"))
                .withOverridden(Responsibility.HANDLING_LINE, "wheel was flat from a hand brake left applied");
        assertThat(defect.answeredBy()).isSameAs(Responsibility.HANDLING_LINE);
    }

    @Test
    void aCardNumberedBelowOneIsRefused() {
        assertThatThrownBy(() -> card(0, CN, "ESC", aCard().defects())).isInstanceOf(BillingError.class);
    }

    @Test
    void aCardWithNoRepairsIsRefused() {
        assertThatThrownBy(() -> aCard(List.of())).isInstanceOf(BillingError.class);
    }

    @Test
    void aCardWrittenAtAPlaceWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> card(88104, CN, "CHI", aCard().defects())).isInstanceOf(MileageError.class);
    }

    @Test
    void aCardListsTheJobsOnIt() {
        RepairCard card =
                aCard(List.of(new Defect(WHEELSET, new WhyMade("01")), new Defect(COUPLER, new WhyMade("03"))));
        assertThat(card.jobs()).containsExactly(WHEELSET, COUPLER);
    }

    @Test
    void laborIsTheHoursAtTheRoadRate() {
        assertThat(priced(aCard()).lines().get(0).labor()).isEqualTo(new Money(10500));
    }

    @Test
    void materialOnANewPartIsTheFullPrice() {
        assertThat(priced(aCard()).lines().get(0).material()).isEqualTo(new Money(128000));
    }

    @Test
    void materialOnAnOldPartIsDepreciated() {
        RepairCard card = aCard(List.of(new Defect(WHEELSET, new WhyMade("01")).withPartYears(5)));
        assertThat(priced(card).lines().get(0).material()).isEqualTo(new Money(102400));
    }

    @Test
    void aQuantityMultipliesBothHalves() {
        RepairCard card = aCard(List.of(new Defect(WHEELSET, new WhyMade("01")).withQuantity(4)));
        PricedCard found = priced(card);
        assertThat(found.lines().get(0).labor()).isEqualTo(new Money(42000));
        assertThat(found.lines().get(0).material()).isEqualTo(new Money(512000));
    }

    @Test
    void aLineTotalsItsTwoHalves() {
        assertThat(priced(aCard()).lines().get(0).amount()).isEqualTo(new Money(10500 + 128000));
    }

    @Test
    void aCardTotalsItsLines() {
        RepairCard card =
                aCard(List.of(new Defect(WHEELSET, new WhyMade("01")), new Defect(COUPLER, new WhyMade("01"))));
        assertThat(priced(card).total()).isEqualTo(new Money(10500 + 128000 + 5250 + 84000));
    }

    @Test
    void wearGoesOnTheOwnerBill() {
        PricedCard found = priced(aCard());
        assertThat(found.billedToOwner()).isEqualTo(found.total());
        assertThat(found.borneByTheRoad()).isEqualTo(new Money(0));
    }

    @Test
    void handlingDamageStaysWithTheRoadThatDidIt() {
        PricedCard found = priced(aCard(List.of(new Defect(COUPLER, new WhyMade("03")))));
        assertThat(found.billedToOwner()).isEqualTo(new Money(0));
        assertThat(found.borneByTheRoad()).isEqualTo(found.total());
    }

    @Test
    void aMixedCardSplitsBetweenTheTwo() {
        RepairCard card =
                aCard(List.of(new Defect(WHEELSET, new WhyMade("01")), new Defect(COUPLER, new WhyMade("03"))));
        PricedCard found = priced(card);
        assertThat(found.billedToOwner()).isEqualTo(new Money(138500));
        assertThat(found.borneByTheRoad()).isEqualTo(new Money(89250));
    }

    @Test
    void jointWorkIsBilledToTheOwner() {
        PricedCard found = priced(aCard(List.of(new Defect(COUPLER, new WhyMade("14")))));
        assertThat(found.billedToOwner()).isEqualTo(found.total());
    }

    @Test
    void aCardReportsWhatEachPartyOwes() {
        RepairCard card =
                aCard(List.of(new Defect(WHEELSET, new WhyMade("01")), new Defect(COUPLER, new WhyMade("04"))));
        PricedCard found = priced(card);
        assertThat(found.owedBy(Responsibility.THIRD_PARTY)).isEqualTo(new Money(89250));
        assertThat(found.owedBy(Responsibility.HANDLING_LINE)).isEqualTo(new Money(0));
    }

    @Test
    void aPricedCardPrintsItsSplit() {
        assertThat(priced(aCard()).toString()).endsWith("$1,385.00 of which $1,385.00 to the owner");
    }

    @Test
    void aRoadWithNoLaborRateCannotPriceACard() {
        RepairCard card = card(88104, new ReportingMark("ELS"), "ESC", aCard().defects());
        assertThatThrownBy(() -> priced(card)).isInstanceOf(BillingError.class);
    }

    @Test
    void aJobWithNoPriceCannotBeBilled() {
        RepairCard card = aCard(List.of(new Defect(new JobCode("9999"), new WhyMade("01"))));
        assertThatThrownBy(() -> priced(card)).isInstanceOf(BillingError.class);
    }
}
