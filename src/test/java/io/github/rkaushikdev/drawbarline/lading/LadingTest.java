package io.github.rkaushikdev.drawbarline.lading;

import static io.github.rkaushikdev.drawbarline.lading.Commodity.IRON_ORE_PELLETS;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.KRAFT_PAPER;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.SODIUM_HYDROXIDE;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.WHEAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LadingTest {

    @Test
    void aCodePrintsInTheGroupsATariffUses() {
        assertThat(new Stcc("0113710")).hasToString("01 137 10");
    }

    @Test
    void aCodeReadsBackWithDashesTakenOut() {
        assertThat(Stcc.parse("01-137-10")).isEqualTo(new Stcc("0113710"));
    }

    @Test
    void aCodeReadsBackWithSpacesTakenOut() {
        assertThat(Stcc.parse(" 01 137 10 ")).isEqualTo(new Stcc("0113710"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "011371", "01137100", "011371X"})
    void aCodeThatIsNotSevenDigitsIsRefused(String bad) {
        assertThatThrownBy(() -> new Stcc(bad)).isInstanceOf(LadingError.class);
    }

    @Test
    void aCodeThatIsNotTextIsRefused() {
        assertThatThrownBy(() -> new Stcc(null)).isInstanceOf(LadingError.class);
    }

    @Test
    void theMajorGroupIsTheFirstTwoDigits() {
        assertThat(new Stcc("0113710").majorGroup()).isEqualTo("01");
    }

    @Test
    void theHazardousGroupIsFortyNine() {
        assertThat(new Stcc("4930040").hazardous()).isTrue();
    }

    @Test
    void anOrdinaryCommodityIsNotHazardous() {
        assertThat(new Stcc("0113710").hazardous()).isFalse();
    }

    @Test
    void aCodeReadsAtAnyLevel() {
        Stcc code = new Stcc("0113710");
        assertThat(code.atLevel(1)).isEqualTo("0");
        assertThat(code.atLevel(4)).isEqualTo("0113");
        assertThat(code.atLevel(7)).isEqualTo("0113710");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, -1})
    void aLevelOutsideTheCodeIsRefused(int level) {
        assertThatThrownBy(() -> new Stcc("0113710").atLevel(level)).isInstanceOf(LadingError.class);
    }

    @Test
    void aCodeSitsInsideItsFamily() {
        assertThat(new Stcc("0113710").within("01")).isTrue();
        assertThat(new Stcc("0113710").within("0113")).isTrue();
    }

    @Test
    void aCodeDoesNotSitInsideAnotherFamily() {
        assertThat(new Stcc("0113710").within("24")).isFalse();
    }

    @Test
    void aFamilyThatIsNotDigitsIsRefused() {
        assertThatThrownBy(() -> new Stcc("0113710").within("grain")).isInstanceOf(LadingError.class);
    }

    @Test
    void aFamilyLongerThanACodeIsRefused() {
        assertThatThrownBy(() -> new Stcc("0113710").within("01137100")).isInstanceOf(LadingError.class);
    }

    @Test
    void aCommodityPrintsItsCodeAndName() {
        assertThat(WHEAT).hasToString("01 137 10 wheat");
    }

    @Test
    void aCommodityNeedsAName() {
        assertThatThrownBy(() -> new Commodity(new Stcc("0113710"), "  ")).isInstanceOf(LadingError.class);
    }

    @Test
    void aCommodityTakesItsHazardFromItsCode() {
        assertThat(SODIUM_HYDROXIDE.hazardous()).isTrue();
        assertThat(KRAFT_PAPER.hazardous()).isFalse();
    }

    @Test
    void bulkCommoditiesAreMarked() {
        assertThat(IRON_ORE_PELLETS.bulk()).isTrue();
        assertThat(KRAFT_PAPER.bulk()).isFalse();
    }

    @Test
    void aLadingPrintsTheCommodityAndTheWeight() {
        assertThat(new Lading(WHEAT, new Weight(198000))).hasToString("wheat 198,000 lb");
    }

    @Test
    void aLadingTakesItsHazardFromTheCommodity() {
        assertThat(new Lading(SODIUM_HYDROXIDE, new Weight(180000)).hazardous()).isTrue();
    }

    @Test
    void aLadingOfNothingIsRefused() {
        assertThatThrownBy(() -> new Lading(WHEAT, new Weight(0))).isInstanceOf(LadingError.class);
    }

    @Test
    void theNamedCommoditiesAllCarryValidCodes() {
        for (Commodity commodity : List.of(WHEAT, KRAFT_PAPER, IRON_ORE_PELLETS, SODIUM_HYDROXIDE)) {
            assertThat(commodity.code().digits()).hasSize(7);
        }
    }

    @Test
    void theNamedCommoditiesHaveDistinctCodes() {
        Set<Stcc> codes = Set.of(WHEAT.code(), KRAFT_PAPER.code(), IRON_ORE_PELLETS.code(), SODIUM_HYDROXIDE.code());
        assertThat(codes).hasSize(4);
    }
}
