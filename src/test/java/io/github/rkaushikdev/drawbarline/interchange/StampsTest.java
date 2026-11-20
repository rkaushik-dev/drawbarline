package io.github.rkaushikdev.drawbarline.interchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StampsTest {

    @Test
    void aFullStampReads() {
        assertThat(Stamps.day("20250106")).contains(LocalDate.of(2025, 1, 6));
        assertThat(Stamps.moment("202501060700")).contains(LocalDateTime.of(2025, 1, 6, 7, 0));
    }

    @Test
    void aShortFieldReadsTheFirstWayThatWorks() {
        assertThat(Stamps.day("2025114")).contains(LocalDate.of(2025, 11, 4));
        assertThat(Stamps.moment("20250106700")).contains(LocalDateTime.of(2025, 1, 6, 7, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2025", "20251301", "20250230", "00000101", "2025010", "2025-01-06", "202501061"})
    void aDayStampThatIsNotADayReadsAsNothing(String text) {
        assertThat(Stamps.day(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "20250106", "202501066000", "202502300700", "2025-01-06 07:00", "2025010607000"})
    void aMomentStampThatIsNotAMomentReadsAsNothing(String text) {
        assertThat(Stamps.moment(text)).isEmpty();
    }

    @Test
    void aStampTheFirstReadingLeavesShortIsReadAnotherWayWhenThatUsesItAll() {
        assertThat(Stamps.moment("20251301")).contains(LocalDateTime.of(2025, 1, 3, 0, 1));
    }

    @Test
    void theLinesOfAFileEndAtAnyLineEnding() {
        assertThat(Sheet.lines("a\nb\r\nc\rd e")).containsExactly("a", "b", "c", "d", "e");
        assertThat(Sheet.lines("a\n\nb\n")).containsExactly("a", "", "b");
        assertThat(Sheet.lines("")).isEmpty();
    }
}
