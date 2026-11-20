package io.github.rkaushikdev.drawbarline.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class UtilTest {

    @Test
    void textIsQuotedAndEscaped() {
        assertThat(Literal.of("PWR")).isEqualTo("'PWR'");
        assertThat(Literal.of("it's")).isEqualTo("\"it's\"");
        assertThat(Literal.of("say \"it's\"")).isEqualTo("'say \"it\\'s\"'");
        assertThat(Literal.of("a\\b\tc\nd")).isEqualTo("'a\\\\b\\tc\\nd'");
        assertThat(Literal.of("\u0001 é ")).isEqualTo("'\\x01\\xa0é\\u2028'");
    }

    @Test
    void valuesOtherThanTextReadAsThemselves() {
        assertThat(Literal.of(null)).isEqualTo("None");
        assertThat(Literal.of(true)).isEqualTo("True");
        assertThat(Literal.of(42)).isEqualTo("42");
        assertThat(Literal.of(17.5)).isEqualTo("17.5");
        assertThat(Literal.of(List.of("OCO", 3))).isEqualTo("['OCO', 3]");
        assertThat(Literal.of(Optional.empty())).isEqualTo("None");
        assertThat(Literal.of(new LinkedHashSet<>())).isEqualTo("set()");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        assertThat(Literal.of(map)).isEqualTo("{'a': 1}");
    }

    @Test
    void plainTextIsLeftAloneAndEverythingElseIsALiteral() {
        assertThat(Literal.plain("PWR")).isEqualTo("PWR");
        assertThat(Literal.plain(List.of("OCO"))).isEqualTo("['OCO']");
        assertThat(Literal.plain(7)).isEqualTo("7");
    }

    @ParameterizedTest
    @CsvSource({
        "0.17, 2, 0.17",
        "96.0, 2, 96.00",
        "0.125, 2, 0.12",
        "0.375, 2, 0.38",
        "2.675, 2, 2.67",
        "33.6, 1, 33.6",
        "-0.001, 2, -0.00",
        "1234.5, 0, 1234"
    })
    void fixedPlacesRoundTheExactValueWithTiesToEven(double value, int places, String expected) {
        assertThat(Numbers.fixed(value, places)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "17.5, 17.5",
        "2.0, 2.0",
        "0.1, 0.1",
        "0.0001, 0.0001",
        "0.00001, 1e-05",
        "1e15, 1000000000000000.0",
        "1e16, 1e+16",
        "123456789.125, 123456789.125",
        "-2.5, -2.5",
        "1.5e-07, 1.5e-07",
        "0.0, 0.0"
    })
    void theGeneralFormIsTheShortestThatReadsBack(double value, String expected) {
        assertThat(Numbers.general(value)).isEqualTo(expected);
    }

    @Test
    void theGeneralFormNamesWhatIsNotANumber() {
        assertThat(Numbers.general(Double.NaN)).isEqualTo("nan");
        assertThat(Numbers.general(Double.NEGATIVE_INFINITY)).isEqualTo("-inf");
        assertThat(Numbers.general(-0.0)).isEqualTo("-0.0");
        assertThat(Numbers.fixed(Double.POSITIVE_INFINITY, 2)).isEqualTo("inf");
    }

    @Test
    void groupedNumbersUseCommas() {
        assertThat(Numbers.grouped(263000)).isEqualTo("263,000");
        assertThat(Numbers.grouped(-1234567)).isEqualTo("-1,234,567");
    }

    static List<String> spaces() {
        return Arrays.asList(" ", "\t", "\n", " ", " ", " ", "　", "\u001c", "\u0085");
    }

    @ParameterizedTest
    @MethodSource("spaces")
    void stripTakesEverySpaceOffBothEnds(String space) {
        assertThat(Text.strip(space + "ESC" + space)).isEqualTo("ESC");
        assertThat(Text.isBlank(space)).isTrue();
    }

    @Test
    void stripLeavesTheMiddleAndEverythingElseAlone() {
        assertThat(Text.strip("a b")).isEqualTo("a b");
        assertThat(Text.strip("x")).isEqualTo("x");
        assertThat(Text.isBlank(" x ")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESC", "ESC1", "A1B2", "ÉTE"})
    void upperCaseNeedsACapitalAndNoSmallLetter(String text) {
        assertThat(Text.isUpper(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "Esc", "esc", "ǅ"})
    void textWithoutCapitalsOrWithASmallLetterIsNotUpperCase(String text) {
        assertThat(Text.isUpper(text)).isFalse();
    }

    @Test
    void lettersAndDigitsAreTestedOverTheWholeText() {
        assertThat(Text.isAlnum("ESC1")).isTrue();
        assertThat(Text.isAlnum("ES C")).isFalse();
        assertThat(Text.isAlnum("")).isFalse();
        assertThat(Text.isAlpha("abc")).isTrue();
        assertThat(Text.isAlpha("ab1")).isFalse();
        assertThat(Text.isDigits("0113710")).isTrue();
        assertThat(Text.isDigits("01-13")).isFalse();
        assertThat(Text.isDigits("")).isFalse();
        assertThat(Text.upper("wc")).isEqualTo("WC");
        assertThat(Text.lower("DEBUG")).isEqualTo("debug");
    }
}
