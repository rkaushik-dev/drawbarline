package io.github.rkaushikdev.drawbarline.scales;

import static io.github.rkaushikdev.drawbarline.lading.Commodity.KRAFT_PAPER;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.WHEAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeightAgreementsTest {

    static final LocalDate JULY = LocalDate.of(2024, 7, 1);
    static final LocalDate CHECKED = LocalDate.of(2025, 2, 3);
    static final CarMark CAR = CarMark.parse("CN 400100");

    static WeightAgreement wheat() {
        return new WeightAgreement("PWRGRN", WHEAT, new Weight(198000), JULY);
    }

    static CheckWeigh weighs(long pounds) {
        return new CheckWeigh("PWRGRN", WHEAT, CAR, CHECKED, new Weight(pounds));
    }

    static WeightAgreements filed(WeightAgreement... agreements) {
        WeightAgreements found = new WeightAgreements();
        for (WeightAgreement one : agreements) {
            found.file(one);
        }
        return found;
    }

    @Test
    void theVarianceIsTenthsOfAPercentOfTheAgreedWeight() {
        assertThat(weighs(199980).varianceTenthsPercent(wheat())).isEqualTo(10);
        assertThat(weighs(196020).varianceTenthsPercent(wheat())).isEqualTo(10);
        assertThat(weighs(198000).varianceTenthsPercent(wheat())).isZero();
        assertThat(weighs(200000).varianceTenthsPercent(wheat())).isEqualTo(10);
        assertThat(weighs(200178).varianceTenthsPercent(wheat())).isEqualTo(11);
    }

    @Test
    void aCheckWeighInsideTheVarianceLeavesTheAgreementStanding() {
        WeightAgreements register = filed(wheat());
        assertThat(register.checkWeigh(weighs(199900))).isEmpty();
        assertThat(register.billingWeight("PWRGRN", WHEAT, CHECKED.plusDays(1), Optional.of(new Weight(199900))))
                .isEqualTo(new Weight(198000));
    }

    @Test
    void aCheckWeighPastTheVarianceSuspendsTheAgreementFromThatDay() {
        WeightAgreements register = filed(wheat());
        assertThat(register.checkWeigh(weighs(201300))).contains(wheat());
        assertThat(register.billingWeight("PWRGRN", WHEAT, CHECKED.minusDays(1), Optional.empty()))
                .isEqualTo(new Weight(198000));
        assertThat(register.billingWeight("PWRGRN", WHEAT, CHECKED, Optional.of(new Weight(201300))))
                .isEqualTo(new Weight(201300));
        assertThat(register.suspendedBy(wheat(), CHECKED)).contains(weighs(201300));
    }

    @Test
    void aSuspendedAgreementWithNoScaleWeightLeavesNothingToBillOn() {
        WeightAgreements register = filed(wheat());
        register.checkWeigh(weighs(190000));
        assertThatThrownBy(() -> register.billingWeight("PWRGRN", WHEAT, CHECKED.plusDays(3), Optional.empty()))
                .isInstanceOf(ScaleError.class)
                .hasMessageContaining("nothing to bill on");
    }

    @Test
    void aNewAgreementAfterTheCheckWeighIsNotTouchedByIt() {
        WeightAgreement first = new WeightAgreement("PWRGRN", WHEAT, new Weight(198000), JULY, CHECKED.plusDays(9));
        WeightAgreement refiled = new WeightAgreement("PWRGRN", WHEAT, new Weight(201000), CHECKED.plusDays(10));
        WeightAgreements register = filed(first, refiled);
        register.checkWeigh(weighs(201300));
        assertThat(register.agreementFor("PWRGRN", WHEAT, CHECKED.plusDays(5))).isEmpty();
        assertThat(register.agreementFor("PWRGRN", WHEAT, CHECKED.plusDays(10))).contains(refiled);
    }

    @Test
    void aCheckWeighOfAnotherCommodityStopsNothing() {
        WeightAgreements register = filed(wheat());
        CheckWeigh paper = new CheckWeigh("PWRGRN", KRAFT_PAPER, CAR, CHECKED, new Weight(120000));
        assertThat(register.checkWeigh(paper)).isEmpty();
        assertThat(register.agreementFor("PWRGRN", WHEAT, CHECKED)).contains(wheat());
        assertThat(register.checkWeighs()).containsExactly(paper);
    }

    @Test
    void filingAnOverlappingAgreementIsRefused() {
        WeightAgreements register = filed(wheat());
        WeightAgreement second = new WeightAgreement("PWRGRN", WHEAT, new Weight(201000), CHECKED);
        assertThatThrownBy(() -> register.file(second))
                .isInstanceOf(ScaleError.class)
                .hasMessageContaining("overlaps one already on file");
        assertThat(register.agreements()).containsExactly(wheat());
    }

    @Test
    void aRegisterReadInKeepsItsClashesAndRefusesToBillOnThem() {
        WeightAgreement second = new WeightAgreement("PWRGRN", WHEAT, new Weight(201000), CHECKED);
        WeightAgreements register = WeightAgreements.of(List.of(wheat(), second));
        assertThat(register.overlapping()).containsExactly(List.of(wheat(), second));
        assertThatThrownBy(() -> register.agreementFor("PWRGRN", WHEAT, CHECKED))
                .isInstanceOf(ScaleError.class);
    }

    @Test
    void aCheckWeighNamesTheShipperAndFindsLading() {
        assertThatThrownBy(() -> new CheckWeigh(" ", WHEAT, CAR, CHECKED, new Weight(1)))
                .isInstanceOf(ScaleError.class);
        assertThatThrownBy(() -> new CheckWeigh("PWRGRN", WHEAT, CAR, CHECKED, Weight.NOTHING))
                .isInstanceOf(ScaleError.class);
        assertThat(weighs(201300)).hasToString("CN 400100 PWRGRN wheat 201,300 lb on 2025-02-03");
    }
}
