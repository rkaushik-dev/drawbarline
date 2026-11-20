package io.github.rkaushikdev.drawbarline.repairs;

import static io.github.rkaushikdev.drawbarline.repairs.Codes.WHY_MADE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepairCodesTest {

    @Test
    void aJobCodePrintsItsDigits() {
        assertThat(new JobCode("4712")).hasToString("4712");
    }

    @Test
    void aJobCodeGroupsByItsFirstTwoDigits() {
        assertThat(new JobCode("4712").family()).isEqualTo("47");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "471", "47120", "47A2", "  "})
    void aJobCodeThatIsNotFourDigitsIsRefused(String bad) {
        assertThatThrownBy(() -> new JobCode(bad)).isInstanceOf(BillingError.class);
    }

    @Test
    void aJobCodeThatIsNotTextIsRefused() {
        assertThatThrownBy(() -> new JobCode(null))
                .isInstanceOf(BillingError.class)
                .hasMessageContaining("a job code is four digits");
    }

    @Test
    void aWhyMadeCodeCarriesItsMeaning() {
        assertThat(new WhyMade("01").text()).isEqualTo("worn beyond limit");
    }

    @Test
    void aWhyMadeCodePrintsItsDigits() {
        assertThat(new WhyMade("03")).hasToString("03");
    }

    @Test
    void aWhyMadeCodeNobodyPublishesIsRefused() {
        assertThatThrownBy(() -> new WhyMade("99")).isInstanceOf(BillingError.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "011", "A1"})
    void aWhyMadeCodeThatIsNotTwoDigitsIsRefused(String bad) {
        assertThatThrownBy(() -> new WhyMade(bad)).isInstanceOf(BillingError.class);
    }

    @Test
    void aWhyMadeCodeReadsBackWithSpaceTakenOff() {
        assertThat(Codes.whyMade(" 03 ")).isEqualTo(new WhyMade("03"));
    }

    @Test
    void wearLandsOnTheOwner() {
        assertThat(new WhyMade("01").usualResponsibility()).isSameAs(Responsibility.OWNER);
    }

    @Test
    void handlingDamageLandsOnTheHandlingLine() {
        assertThat(new WhyMade("03").usualResponsibility()).isSameAs(Responsibility.HANDLING_LINE);
    }

    @Test
    void ladingDamageLandsOnAThirdParty() {
        assertThat(new WhyMade("04").usualResponsibility()).isSameAs(Responsibility.THIRD_PARTY);
    }

    @Test
    void aWronglyAppliedPartIsJoint() {
        assertThat(new WhyMade("14").usualResponsibility()).isSameAs(Responsibility.JOINT);
    }

    @Test
    void everyPublishedCodeCarriesTextAndAParty() {
        for (WhyMadeMeaning meaning : WHY_MADE.values()) {
            assertThat(meaning.text()).isNotEmpty();
            assertThat(meaning.usual()).isInstanceOf(Responsibility.class);
        }
    }

    @Test
    void everyPublishedCodeIsTwoDigits() {
        assertThat(WHY_MADE.keySet()).allSatisfy(code -> {
            assertThat(code).hasSize(2);
            assertThat(Text.isDigits(code)).isTrue();
        });
    }

    @Test
    void theCodesOnePartyAnswersForComeBackTogether() {
        List<String> owner = Codes.codesAnsweredBy(Responsibility.OWNER);
        assertThat(owner).contains("01");
        assertThat(owner).doesNotContain("03");
    }

    @Test
    void theListsTogetherCoverEveryCode() {
        Set<String> gathered = new HashSet<>();
        for (Responsibility who : Responsibility.values()) {
            gathered.addAll(Codes.codesAnsweredBy(who));
        }
        assertThat(gathered).isEqualTo(WHY_MADE.keySet());
    }

    @Test
    void responsibilityFallsOutOfTheCodeByDefault() {
        assertThat(Codes.responsibilityFor(new WhyMade("01"))).isSameAs(Responsibility.OWNER);
    }

    @Test
    void anInspectorCanSayOtherwise() {
        Responsibility found = Codes.responsibilityFor(new WhyMade("01"), Optional.of(Responsibility.HANDLING_LINE));
        assertThat(found).isSameAs(Responsibility.HANDLING_LINE);
    }

    @Test
    void theOwnerAndAJointFindingAreBothBillableToTheOwner() {
        assertThat(Responsibility.OWNER.billableToOwner()).isTrue();
        assertThat(Responsibility.JOINT.billableToOwner()).isTrue();
    }

    @Test
    void aHandlingLineFindingIsNotBilledToTheOwner() {
        assertThat(Responsibility.HANDLING_LINE.billableToOwner()).isFalse();
    }

    @Test
    void everyPartyHasReadableText() {
        assertThat(Responsibility.values())
                .allSatisfy(who -> assertThat(who.text()).isNotEmpty());
    }

    @Test
    void codesCompareByValueAndHash() {
        assertThat(new WhyMade("01")).isEqualTo(new WhyMade("01"));
        assertThat(Set.copyOf(List.of(new JobCode("4712"), new JobCode("4712"))))
                .hasSize(1);
    }
}
