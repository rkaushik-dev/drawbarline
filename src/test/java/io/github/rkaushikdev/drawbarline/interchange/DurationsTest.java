package io.github.rkaushikdev.drawbarline.interchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void aSpanUnderADayReadsAsHoursMinutesAndSeconds() {
        assertThat(Durations.text(Duration.ofMinutes(30))).isEqualTo("0:30:00");
        assertThat(Durations.text(Duration.ofSeconds(3725))).isEqualTo("1:02:05");
        assertThat(Durations.text(Duration.ZERO)).isEqualTo("0:00:00");
    }

    @Test
    void wholeDaysAreCountedInFront() {
        assertThat(Durations.text(Duration.ofDays(1).plusHours(2))).isEqualTo("1 day, 2:00:00");
        assertThat(Durations.text(Duration.ofDays(3))).isEqualTo("3 days, 0:00:00");
    }

    @Test
    void aNegativeSpanIsWholeDaysBelowZeroAndAPositiveRest() {
        assertThat(Durations.text(Duration.ofMinutes(-1))).isEqualTo("-1 day, 23:59:00");
        assertThat(Durations.text(Duration.ofDays(-2).plusHours(1))).isEqualTo("-2 days, 1:00:00");
    }

    @Test
    void microsecondsShowOnlyWhenThereAreSome() {
        assertThat(Durations.text(Duration.ofSeconds(1, 250_000))).isEqualTo("0:00:01.000250");
        assertThat(Durations.text(Duration.ofNanos(-1_000))).isEqualTo("-1 day, 23:59:59.999999");
    }
}
