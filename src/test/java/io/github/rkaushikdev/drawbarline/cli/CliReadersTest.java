package io.github.rkaushikdev.drawbarline.cli;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CliReadersTest {

    @TempDir
    Path tmp;

    @Test
    void aDayReadsBack() {
        assertThat(Readers.aDate("2025-01-14")).isEqualTo(LocalDate.of(2025, 1, 14));
    }

    @Test
    void aDayWithSpaceAroundItReadsBack() {
        assertThat(Readers.aDate("  2025-01-14 ")).isEqualTo(LocalDate.of(2025, 1, 14));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "january", "2025-13-01", "14-01-2025"})
    void aDayThatCannotBeReadIsRefused(String bad) {
        assertThatThrownBy(() -> Readers.aDate(bad))
                .isInstanceOf(CommandError.class)
                .hasMessageContaining("yyyy-mm-dd");
    }

    @Test
    void aMonthReadsBack() {
        assertThat(Readers.aMonth("2025-01")).isEqualTo(new Period(2025, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "january", "2025-13", "2025-01-14"})
    void aMonthThatCannotBeReadIsRefused(String bad) {
        assertThatThrownBy(() -> Readers.aMonth(bad))
                .isInstanceOf(CommandError.class)
                .hasMessageContaining("yyyy-mm");
    }

    @Test
    void aReadingIsReadAtTheStation() {
        assertThat(Readers.aMoment("ESC", "2025-01-14 09:00")).isEqualTo(utc(2025, 1, 14, 14, 0));
    }

    @Test
    void theSameReadingAtMarinetteIsAnHourLater() {
        assertThat(Readers.aMoment("MRN", "2025-01-14 09:00")).isEqualTo(utc(2025, 1, 14, 15, 0));
    }

    @Test
    void aReadingThatCannotBeReadIsRefused() {
        assertThatThrownBy(() -> Readers.aMoment("ESC", "yesterday afternoon"))
                .isInstanceOf(CommandError.class)
                .hasMessageContaining("yyyy-mm-dd hh:mm");
    }

    @Test
    void aReadingThatNeverHappenedIsRefused() {
        assertThatThrownBy(() -> Readers.aMoment("PWR", "2025-03-09 02:30")).isInstanceOf(DrawbarlineError.class);
    }

    @Test
    void theSecondPassTakesTheLaterHour() {
        Instant first = Readers.aMoment("PWR", "2025-11-02 01:30");
        Instant second = Readers.aMoment("PWR", "2025-11-02 01:30", true);
        assertThat(Duration.between(first, second).toSeconds()).isEqualTo(3600);
    }

    @Test
    void aStationWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> Readers.aMoment("CHI", "2025-01-14 09:00")).isInstanceOf(DrawbarlineError.class);
    }

    @Test
    void aSheetReadsBackAsRows() throws IOException {
        Path sheet = tmp.resolve("one.csv");
        Files.writeString(sheet, "a,b\n1,2\n3,4\n", StandardCharsets.UTF_8);
        assertThat(Readers.rowsOf(sheet)).isEqualTo(List.of(Map.of("a", "1", "b", "2"), Map.of("a", "3", "b", "4")));
    }

    @Test
    void anEmptySheetReadsBackAsNothing() throws IOException {
        Path sheet = tmp.resolve("empty.csv");
        Files.writeString(sheet, "a,b\n", StandardCharsets.UTF_8);
        assertThat(Readers.rowsOf(sheet)).isEmpty();
    }

    @Test
    void aSheetWithTheColumnsItNeedsPasses() {
        assertThatCode(() -> Readers.requireColumns(List.of(Map.of("a", "1", "b", "2")), Set.of("a", "b"), "one.csv"))
                .doesNotThrowAnyException();
    }

    @Test
    void aSheetMissingAColumnIsRefused() {
        assertThatThrownBy(() -> Readers.requireColumns(List.of(Map.of("a", "1")), Set.of("a", "b"), "one.csv"))
                .isInstanceOf(CommandError.class)
                .hasMessageContaining("one.csv")
                .hasMessageContaining("'b'");
    }

    @Test
    void aSheetWithNoRowsAtAllIsRefused() {
        assertThatThrownBy(() -> Readers.requireColumns(List.of(), Set.of("a"), "empty.csv"))
                .isInstanceOf(CommandError.class);
    }

    @Test
    void aDayReadsAsMidnightUtc() {
        Instant found = Readers.aMomentUtc(Optional.of("2025-01-14"));
        assertThat(found).isEqualTo(utc(2025, 1, 14));
        assertThat(Clock.iso(found)).endsWith("+00:00");
    }

    @Test
    void noDayReadsAsNow() {
        Instant before = Clock.now();
        Instant found = Readers.aMomentUtc(Optional.empty());
        assertThat(found).isBetween(before, Clock.now());
    }

    @Test
    void aDayThatCannotBeReadIsRefusedHereToo() {
        assertThatThrownBy(() -> Readers.aMomentUtc(Optional.of("tuesday"))).isInstanceOf(CommandError.class);
    }

    @Test
    void aClockReadingReadsBack() {
        assertThat(Readers.aTime("07:00")).isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    void aClockReadingWithSpaceAroundItReadsBack() {
        assertThat(Readers.aTime(" 19:30 ")).isEqualTo(LocalTime.of(19, 30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "morning", "25:00", "7"})
    void aClockReadingThatCannotBeReadIsRefused(String bad) {
        assertThatThrownBy(() -> Readers.aTime(bad))
                .isInstanceOf(CommandError.class)
                .hasMessageContaining("hh:mm");
    }

    @Test
    void aQuotedFieldKeepsItsCommasQuotesAndLineBreaks() throws IOException {
        Path sheet = tmp.resolve("quoted.csv");
        Files.writeString(
                sheet,
                "name,note\r\n\"Paper, Inc\",\"said \"\"no\"\"\nthen yes\"\r\n\"4312\"a,b\r\n",
                StandardCharsets.UTF_8);
        assertThat(Readers.rowsOf(sheet))
                .containsExactly(
                        Map.of("name", "Paper, Inc", "note", "said \"no\"\nthen yes"),
                        Map.of("name", "4312a", "note", "b"));
    }

    @Test
    void aBlankLineIsNotARowAndAShortRowReadsEmpty() throws IOException {
        Path sheet = tmp.resolve("ragged.csv");
        Files.writeString(sheet, "a,b,c\n\n1\r\n2,3,4,5\r", StandardCharsets.UTF_8);
        assertThat(Readers.rowsOf(sheet))
                .containsExactly(Map.of("a", "1", "b", "", "c", ""), Map.of("a", "2", "b", "3", "c", "4"));
    }

    @Test
    void aWholeNumberReadsWithItsSignAndSpace() {
        assertThat(Readers.whole(" -1_000 ")).isEqualTo(-1000);
        assertThat(Readers.whole("+42")).isEqualTo(42);
        assertThatThrownBy(() -> Readers.whole("4.5")).isInstanceOf(CommandError.class);
        assertThatThrownBy(() -> Readers.whole("1__0")).isInstanceOf(CommandError.class);
        assertThatThrownBy(() -> Readers.whole("99999999999")).isInstanceOf(CommandError.class);
    }
}
