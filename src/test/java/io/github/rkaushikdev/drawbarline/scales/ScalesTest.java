package io.github.rkaushikdev.drawbarline.scales;

import static io.github.rkaushikdev.drawbarline.lading.Commodity.KRAFT_PAPER;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.WHEAT;
import static io.github.rkaushikdev.drawbarline.scales.Scales.CERTIFICATION_DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScalesTest {

    static final LocalDate TESTED = LocalDate.of(2025, 1, 6);

    static final class ScaleFields {
        String code = "ESC1";
        String station = "ESC";
        Weight capacity = new Weight(400000);

        Scale build() {
            return new Scale(code, station, capacity);
        }
    }

    static final class AgreementFields {
        String shipper = "PWRGRN";
        Commodity commodity = WHEAT;
        Weight agreed = new Weight(198000);
        LocalDate effective = LocalDate.of(2024, 7, 1);
        Optional<LocalDate> expires = Optional.empty();

        WeightAgreement build() {
            return new WeightAgreement(shipper, commodity, agreed, effective, expires);
        }
    }

    static Scale aScale() {
        return new ScaleFields().build();
    }

    static ScaleTest aTest() {
        return aTest(TESTED, Result.PASSED, 1, "ESC1");
    }

    static ScaleTest aTest(LocalDate on) {
        return aTest(on, Result.PASSED, 1, "ESC1");
    }

    static ScaleTest aTest(Result result) {
        return aTest(TESTED, result, 1, "ESC1");
    }

    static ScaleTest anError(int error) {
        return aTest(TESTED, Result.PASSED, error, "ESC1");
    }

    static ScaleTest aTest(LocalDate on, Result result, int error, String scale) {
        return new ScaleTest(scale, on, result, error, "state weights and measures");
    }

    static WeightAgreement anAgreement() {
        return new AgreementFields().build();
    }

    static WeightAgreement expiring(LocalDate expires) {
        AgreementFields changed = new AgreementFields();
        changed.expires = Optional.of(expires);
        return changed.build();
    }

    @Test
    void aScalePrintsItsCodeAndPlace() {
        assertThat(aScale()).hasToString("ESC1 at ESC");
    }

    @Test
    void aScaleCodeIsUpperCase() {
        ScaleFields changed = new ScaleFields();
        changed.code = "esc1";
        assertThatThrownBy(changed::build).isInstanceOf(ScaleError.class);
    }

    @Test
    void aScaleAtAPlaceWeDoNotServeIsRefused() {
        ScaleFields changed = new ScaleFields();
        changed.station = "CHI";
        assertThatThrownBy(changed::build).isInstanceOf(MileageError.class);
    }

    @Test
    void aScaleThatWeighsNothingIsRefused() {
        ScaleFields changed = new ScaleFields();
        changed.capacity = new Weight(0);
        assertThatThrownBy(changed::build).isInstanceOf(ScaleError.class);
    }

    @Test
    void aTestPrintsItsResult() {
        assertThat(aTest()).hasToString("ESC1 passed on 2025-01-06");
    }

    @Test
    void aTestNamesWhoDidIt() {
        assertThatThrownBy(() -> new ScaleTest("ESC1", TESTED, Result.PASSED, 1, "  "))
                .isInstanceOf(ScaleError.class);
    }

    @Test
    void anErrorIsStatedAsAMagnitude() {
        assertThatThrownBy(() -> anError(-1)).isInstanceOf(ScaleError.class);
    }

    @Test
    void aSmallErrorIsInsideTolerance() {
        assertThat(anError(2).insideTolerance()).isTrue();
    }

    @Test
    void aLargeErrorIsOutsideTolerance() {
        assertThat(anError(5).insideTolerance()).isFalse();
    }

    @Test
    void aPassCertifiesForAYear() {
        assertThat(aTest().certifiesUntil()).isEqualTo(TESTED.plusDays(CERTIFICATION_DAYS));
    }

    @Test
    void aFailedTestLeavesTheScaleUnusable() {
        assertThat(Result.FAILED.usable()).isFalse();
        assertThat(Result.ADJUSTED.usable()).isTrue();
    }

    @Test
    void theLastTestIsTheMostRecentOne() {
        List<ScaleTest> tests = List.of(aTest(TESTED), aTest(LocalDate.of(2025, 6, 1)));
        assertThat(Scales.lastTest(tests, "ESC1", LocalDate.of(2025, 8, 1))
                        .orElseThrow()
                        .testedOn())
                .isEqualTo(LocalDate.of(2025, 6, 1));
    }

    @Test
    void aTestAfterTheDayIsNotTheLastOne() {
        List<ScaleTest> tests = List.of(aTest(TESTED), aTest(LocalDate.of(2025, 6, 1)));
        assertThat(Scales.lastTest(tests, "ESC1", LocalDate.of(2025, 3, 1))
                        .orElseThrow()
                        .testedOn())
                .isEqualTo(TESTED);
    }

    @Test
    void aScaleNeverTestedHasNoLastTest() {
        assertThat(Scales.lastTest(List.of(), "ESC1", LocalDate.of(2025, 3, 1))).isEmpty();
    }

    @Test
    void aTestOnAnotherScaleDoesNotCount() {
        List<ScaleTest> tests = List.of(aTest(TESTED, Result.PASSED, 1, "PWR1"));
        assertThat(Scales.lastTest(tests, "ESC1", LocalDate.of(2025, 3, 1))).isEmpty();
    }

    @Test
    void aScaleInsideItsYearIsCertified() {
        assertThat(Scales.certifiedOn(List.of(aTest()), "ESC1", LocalDate.of(2025, 6, 1)))
                .isTrue();
    }

    @Test
    void aScalePastItsYearIsNotCertified() {
        assertThat(Scales.certifiedOn(List.of(aTest()), "ESC1", LocalDate.of(2026, 6, 1)))
                .isFalse();
    }

    @Test
    void aScaleThatFailedItsTestIsNotCertified() {
        assertThat(Scales.certifiedOn(List.of(aTest(Result.FAILED)), "ESC1", LocalDate.of(2025, 2, 1)))
                .isFalse();
    }

    @Test
    void aScaleAdjustedAndPassedIsCertified() {
        assertThat(Scales.certifiedOn(List.of(aTest(Result.ADJUSTED)), "ESC1", LocalDate.of(2025, 2, 1)))
                .isTrue();
    }

    @Test
    void aScaleNeverTestedIsNotCertified() {
        assertThat(Scales.certifiedOn(List.of(), "ESC1", LocalDate.of(2025, 2, 1)))
                .isFalse();
    }

    @Test
    void aWeightOnACertifiedScaleStands() {
        assertThatCode(() -> Scales.requireCertified(List.of(aTest()), "ESC1", LocalDate.of(2025, 6, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void aWeightOnAnUncertifiedScaleIsRefused() {
        assertThatThrownBy(() -> Scales.requireCertified(List.of(aTest()), "ESC1", LocalDate.of(2026, 6, 1)))
                .isInstanceOf(ScaleError.class);
    }

    @Test
    void anAgreementPrintsTheShipperAndTheWeight() {
        assertThat(anAgreement()).hasToString("PWRGRN wheat at 198,000 lb");
    }

    @Test
    void anAgreementWithNoShipperIsRefused() {
        AgreementFields changed = new AgreementFields();
        changed.shipper = "  ";
        assertThatThrownBy(changed::build).isInstanceOf(ScaleError.class);
    }

    @Test
    void anAgreedWeightOfNothingIsRefused() {
        AgreementFields changed = new AgreementFields();
        changed.agreed = new Weight(0);
        assertThatThrownBy(changed::build).isInstanceOf(ScaleError.class);
    }

    @Test
    void anAgreementThatExpiresFirstIsRefused() {
        assertThatThrownBy(() -> expiring(LocalDate.of(2024, 1, 1))).isInstanceOf(ScaleError.class);
    }

    @Test
    void anAgreementAppliesToItsShipperAndCommodity() {
        WeightAgreement found = anAgreement();
        assertThat(found.appliesTo("PWRGRN", WHEAT, LocalDate.of(2025, 1, 14))).isTrue();
        assertThat(found.appliesTo("ESCPAP", WHEAT, LocalDate.of(2025, 1, 14))).isFalse();
        assertThat(found.appliesTo("PWRGRN", KRAFT_PAPER, LocalDate.of(2025, 1, 14)))
                .isFalse();
    }

    @Test
    void anExpiredAgreementDoesNotApply() {
        WeightAgreement found = expiring(LocalDate.of(2024, 12, 31));
        assertThat(found.appliesTo("PWRGRN", WHEAT, LocalDate.of(2025, 1, 14))).isFalse();
    }

    @Test
    void theAgreementIsTheBillingWeight() {
        Weight found = Scales.billingWeight(List.of(anAgreement()), "PWRGRN", WHEAT, LocalDate.of(2025, 1, 14));
        assertThat(found).isEqualTo(new Weight(198000));
    }

    @Test
    void theAgreementBeatsTheScale() {
        Weight found = Scales.billingWeight(
                List.of(anAgreement()), "PWRGRN", WHEAT, LocalDate.of(2025, 1, 14), new Weight(197600));
        assertThat(found).isEqualTo(new Weight(198000));
    }

    @Test
    void withoutAnAgreementTheScaleBills() {
        Weight found = Scales.billingWeight(List.of(), "PWRGRN", WHEAT, LocalDate.of(2025, 1, 14), new Weight(197600));
        assertThat(found).isEqualTo(new Weight(197600));
    }

    @Test
    void withNeitherThereIsNothingToBillOn() {
        assertThatThrownBy(() -> Scales.billingWeight(List.of(), "PWRGRN", WHEAT, LocalDate.of(2025, 1, 14)))
                .isInstanceOf(ScaleError.class);
    }

    static WeightAgreement from(LocalDate effective, Optional<LocalDate> expires, Weight agreed) {
        AgreementFields changed = new AgreementFields();
        changed.effective = effective;
        changed.expires = expires;
        changed.agreed = agreed;
        return changed.build();
    }

    @Test
    void anAgreementsTermIsItsEffectiveAndLastDay() {
        WeightAgreement found = expiring(LocalDate.of(2025, 6, 30));
        assertThat(found.term()).hasToString("2024-07-01 to 2025-06-30");
        assertThat(found.covers(LocalDate.of(2025, 6, 30))).isTrue();
        assertThat(found.covers(LocalDate.of(2025, 7, 1))).isFalse();
    }

    @Test
    void twoAgreementsCoveringTheSameDayAreNotAChoice() {
        WeightAgreement first = anAgreement();
        WeightAgreement second = from(LocalDate.of(2025, 1, 1), Optional.empty(), new Weight(201000));
        assertThatThrownBy(() -> Scales.billingWeight(
                        List.of(first, second), "PWRGRN", WHEAT, LocalDate.of(2025, 1, 14), new Weight(197600)))
                .isInstanceOf(ScaleError.class)
                .hasMessageContaining("two weight agreements");
    }

    @Test
    void anAgreementThatTakesOverTheDayAfterBills() {
        WeightAgreement first = expiring(LocalDate.of(2024, 12, 31));
        WeightAgreement second = from(LocalDate.of(2025, 1, 1), Optional.empty(), new Weight(201000));
        List<WeightAgreement> both = List.of(first, second);
        assertThat(Scales.billingWeight(both, "PWRGRN", WHEAT, LocalDate.of(2024, 12, 31)))
                .isEqualTo(new Weight(198000));
        assertThat(Scales.billingWeight(both, "PWRGRN", WHEAT, LocalDate.of(2025, 1, 1)))
                .isEqualTo(new Weight(201000));
        assertThat(Scales.overlapping(both)).isEmpty();
    }

    @Test
    void overlappingAgreementsAreFoundBeforeAnythingIsBilled() {
        WeightAgreement first = anAgreement();
        WeightAgreement second = from(LocalDate.of(2025, 1, 1), Optional.empty(), new Weight(201000));
        AgreementFields paper = new AgreementFields();
        paper.commodity = KRAFT_PAPER;
        WeightAgreement other = paper.build();
        assertThat(Scales.overlapping(List.of(first, other, second))).containsExactly(List.of(first, second));
        assertThat(Scales.clash(first, other)).isFalse();
        assertThat(Scales.clash(second, first)).isTrue();
    }

    @Test
    void anotherShippersAgreementNeverClashes() {
        AgreementFields elsewhere = new AgreementFields();
        elsewhere.shipper = "ESCPAP";
        assertThat(Scales.clash(anAgreement(), elsewhere.build())).isFalse();
        assertThat(Scales.agreementFor(List.of(elsewhere.build()), "PWRGRN", WHEAT, LocalDate.of(2025, 1, 14)))
                .isEmpty();
    }
}
