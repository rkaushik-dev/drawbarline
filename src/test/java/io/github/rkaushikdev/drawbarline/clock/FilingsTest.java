package io.github.rkaushikdev.drawbarline.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.RateError;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FilingsTest {

    /** Something filed from a day, which is all a run of filings needs to know about it. */
    record Notice(String name, LocalDate effective) {}

    static Filings<Notice> aRun() {
        Filings<Notice> found = new Filings<>(Notice::effective);
        found.file(new Notice("summer", LocalDate.of(2025, 7, 1)), () -> new RateError("twice"));
        found.file(new Notice("first", LocalDate.of(2024, 7, 1)), () -> new RateError("twice"));
        return found;
    }

    @Test
    void theFilingInForceIsTheLatestThatHadTakenEffect() {
        assertThat(aRun().inForceOn(LocalDate.of(2025, 6, 30)).orElseThrow().name())
                .isEqualTo("first");
        assertThat(aRun().inForceOn(LocalDate.of(2025, 7, 1)).orElseThrow().name())
                .isEqualTo("summer");
        assertThat(aRun().inForceOn(LocalDate.of(2099, 1, 1)).orElseThrow().name())
                .isEqualTo("summer");
    }

    @Test
    void nothingIsInForceBeforeTheFirstFiling() {
        assertThat(aRun().inForceOn(LocalDate.of(2024, 6, 30))).isEmpty();
        assertThat(new Filings<>(Notice::effective).inForceOn(LocalDate.of(2025, 1, 1)))
                .isEmpty();
    }

    @Test
    void filingsAreKeptInTheOrderTheyTakeEffect() {
        assertThat(aRun().all()).extracting(Notice::name).containsExactly("first", "summer");
        assertThat(aRun().effectiveDates()).containsExactly(LocalDate.of(2024, 7, 1), LocalDate.of(2025, 7, 1));
        assertThat(aRun().size()).isEqualTo(2);
    }

    @Test
    void aSecondFilingTheSameDayIsRefusedInTheFilingsOwnWords() {
        Filings<Notice> run = aRun();
        assertThatThrownBy(
                        () -> run.file(new Notice("again", LocalDate.of(2025, 7, 1)), () -> new RateError("planted")))
                .isInstanceOf(RateError.class)
                .hasMessageContaining("planted");
        assertThat(run.size()).isEqualTo(2);
    }
}
