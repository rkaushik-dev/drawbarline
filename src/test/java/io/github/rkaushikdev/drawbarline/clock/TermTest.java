package io.github.rkaushikdev.drawbarline.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.scales.ScaleError;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TermTest {

    static final LocalDate JAN_1 = LocalDate.of(2025, 1, 1);
    static final LocalDate JUN_30 = LocalDate.of(2025, 6, 30);

    @Test
    void aTermCoversBothOfItsEnds() {
        Term found = Term.through(JAN_1, JUN_30);
        assertThat(found.covers(JAN_1)).isTrue();
        assertThat(found.covers(JUN_30)).isTrue();
        assertThat(found.covers(JAN_1.minusDays(1))).isFalse();
        assertThat(found.covers(JUN_30.plusDays(1))).isFalse();
    }

    @Test
    void anOpenTermRunsOnForever() {
        Term found = Term.from(JAN_1);
        assertThat(found.openEnded()).isTrue();
        assertThat(found.covers(LocalDate.of(2099, 12, 31))).isTrue();
        assertThat(found.covers(JAN_1.minusDays(1))).isFalse();
    }

    @Test
    void aTermThatEndsBeforeItBeginsIsRefused() {
        assertThatThrownBy(() -> Term.through(JUN_30, JAN_1))
                .isInstanceOf(ClockError.class)
                .hasMessageContaining("cannot end before it begins");
    }

    @Test
    void aOneDayTermIsATerm() {
        Term found = Term.through(JAN_1, JAN_1);
        assertThat(found.covers(JAN_1)).isTrue();
        assertThat(found.covers(JAN_1.plusDays(1))).isFalse();
    }

    @Test
    void aCheckedTermRefusesWithTheFilingsOwnError() {
        assertThatThrownBy(() -> Term.checked(JUN_30, Optional.of(JAN_1), () -> new ScaleError("planted")))
                .isInstanceOf(ScaleError.class)
                .hasMessageContaining("planted");
        assertThat(Term.checked(JAN_1, Optional.of(JUN_30), () -> new ScaleError("planted")))
                .isEqualTo(Term.through(JAN_1, JUN_30));
    }

    @Test
    void termsSharingOnlyTheirEdgeDayOverlap() {
        Term first = Term.through(JAN_1, JUN_30);
        assertThat(first.overlaps(Term.from(JUN_30))).isTrue();
        assertThat(first.overlaps(Term.from(JUN_30.plusDays(1)))).isFalse();
        assertThat(Term.from(JUN_30.plusDays(1)).overlaps(first)).isFalse();
    }

    @Test
    void twoOpenTermsAlwaysOverlap() {
        assertThat(Term.from(JAN_1).overlaps(Term.from(JUN_30))).isTrue();
        assertThat(Term.from(JUN_30).overlaps(Term.through(JAN_1, JUN_30.minusDays(1))))
                .isFalse();
    }

    @Test
    void endingATermGivesItALastDay() {
        Term found = Term.from(JAN_1).endingOn(JUN_30);
        assertThat(found).isEqualTo(Term.through(JAN_1, JUN_30));
        assertThat(found.openEnded()).isFalse();
        assertThatThrownBy(() -> Term.from(JUN_30).endingOn(JAN_1)).isInstanceOf(ClockError.class);
    }

    @Test
    void aTermPrintsItsDays() {
        assertThat(Term.through(JAN_1, JUN_30)).hasToString("2025-01-01 to 2025-06-30");
        assertThat(Term.from(JAN_1)).hasToString("2025-01-01 to open");
    }
}
