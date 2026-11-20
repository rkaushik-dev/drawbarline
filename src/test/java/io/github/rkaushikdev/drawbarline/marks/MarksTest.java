package io.github.rkaushikdev.drawbarline.marks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.drawbarline.MarkError;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MarksTest {

    @Test
    void aMarkOfTwoLettersIsFine() {
        assertThat(new ReportingMark("CN")).hasToString("CN");
    }

    @Test
    void aMarkOfFourLettersIsFine() {
        assertThat(new ReportingMark("DBLX")).hasToString("DBLX");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "D", "TOOLONG", "DB1", "db", "DB "})
    void aMarkThatIsNotTwoToFourCapitalsIsRefused(String bad) {
        assertThatThrownBy(() -> new ReportingMark(bad)).isInstanceOf(MarkError.class);
    }

    @Test
    void aMarkThatIsNotAStringIsRefused() {
        assertThatThrownBy(() -> new ReportingMark(null))
                .isInstanceOf(MarkError.class)
                .hasMessageContaining("a reporting mark is letters");
    }

    @Test
    void parseTakesLowerCaseAndSurroundingSpace() {
        assertThat(ReportingMark.parse("  wc ")).isEqualTo(new ReportingMark("WC"));
    }

    @Test
    void aTrailingXMeansTheOwnerIsNotARailroad() {
        assertThat(new ReportingMark("PLMX").isPrivate()).isTrue();
        assertThat(new ReportingMark("WC").isPrivate()).isFalse();
    }

    @Test
    void theRoadDropsAPrivateSuffix() {
        assertThat(new ReportingMark("PLMX").road()).isEqualTo("PLM");
        assertThat(new ReportingMark("WC").road()).isEqualTo("WC");
    }

    @Test
    void marksSortAlphabetically() {
        assertThat(List.of(new ReportingMark("WC"), new ReportingMark("CN")).stream()
                        .sorted()
                        .toList())
                .containsExactly(new ReportingMark("CN"), new ReportingMark("WC"));
    }

    @Test
    void marksAreHashableAndCompareByValue() {
        assertThat(Set.of(new ReportingMark("CN")))
                .isEqualTo(new java.util.HashSet<>(List.of(new ReportingMark("CN"), new ReportingMark("CN"))));
    }

    @ParameterizedTest
    @CsvSource({"1, 8", "104207, 6", "999999, 6", "450018, 7", "0, 0"})
    void theCheckDigitMatchesWorkedExamples(int number, int digit) {
        assertThat(Marks.checkDigit(number)).isEqualTo(digit);
    }

    @Test
    void theCheckDigitCatchesATransposition() {
        assertThat(Marks.checkDigit(104207)).isNotEqualTo(Marks.checkDigit(140207));
    }

    @Test
    void aNumberPastSixDigitsHasNoCheckDigit() {
        assertThatThrownBy(() -> Marks.checkDigit(1000000)).isInstanceOf(MarkError.class);
    }

    @Test
    void aCarReadsBackPadded() {
        assertThat(new CarMark(new ReportingMark("DBLX"), 4207)).hasToString("DBLX 004207");
    }

    @Test
    void theStencilCarriesTheCheckDigit() {
        assertThat(new CarMark(new ReportingMark("DBLX"), 104207).stencilled()).isEqualTo("DBLX 104207-6");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DBLX 104207", "dblx104207", "DBLX.104207", "DBLX 104207-6"})
    void parseReadsTheFormsAClerkWrites(String text) {
        assertThat(CarMark.parse(text)).isEqualTo(new CarMark(new ReportingMark("DBLX"), 104207));
    }

    @Test
    void parseRefusesAStencilWhoseCheckDigitDisagrees() {
        MarkError caught = catchThrowableOfType(MarkError.class, () -> CarMark.parse("DBLX 104207-4"));
        assertThat(caught.context().get("expected")).isEqualTo(6);
    }

    @Test
    void parseRefusesSomethingThatIsNotACar() {
        assertThatThrownBy(() -> CarMark.parse("westbound local")).isInstanceOf(MarkError.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1000000})
    void aCarNumberOutOfRangeIsRefused(int bad) {
        assertThatThrownBy(() -> new CarMark(new ReportingMark("WC"), bad)).isInstanceOf(MarkError.class);
    }

    @Test
    void aBooleanIsNotACarNumber() {
        // A car number is a whole number; the compiler refuses a flag in its place.
        assertThat(CarMark.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(ReportingMark.class, int.class);
    }

    @Test
    void aCarKnowsItsOwnerIsPrivate() {
        assertThat(CarMark.parse("PLMX 1").isPrivate()).isTrue();
    }

    @Test
    void carsSortByMarkThenNumber() {
        List<CarMark> cars = List.of(CarMark.parse("WC 100"), CarMark.parse("CN 900"), CarMark.parse("CN 100"));
        assertThat(cars.stream().sorted().map(CarMark::toString).toList())
                .containsExactly("CN 000100", "CN 000900", "WC 000100");
    }
}
