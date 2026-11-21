package io.github.rkaushikdev.drawbarline.persistence;

import static io.github.rkaushikdev.drawbarline.persistence.Numbering.INVOICES;
import static io.github.rkaushikdev.drawbarline.persistence.Numbering.STATEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.persistence.models.CounterRow;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumberingTest {

    private static Database empty() {
        return TestDatabase.fresh();
    }

    private static Database opened() {
        return TestDatabase.seeded(work -> {
            work.numbering().start(INVOICES, 5001);
            work.numbering().start(STATEMENTS, 9001);
        });
    }

    @Test
    void theCounterTableIsOnTheMetadata() {
        assertThat(Database.modelTables()).contains("counter");
    }

    @Test
    void aCounterRowReprNamesTheSeries() {
        assertThat(new CounterRow(INVOICES, 5001)).hasToString("<CounterRow demurrage_invoice next 5001>");
    }

    @Test
    void aSeriesStartsWhereItIsTold() {
        try (UnitOfWork work = opened().unitOfWork()) {
            assertThat(work.numbering().peek(INVOICES)).isEqualTo(5001);
        }
    }

    @Test
    void aSeriesNobodyPublishesIsRefused() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> work.numbering().start("something else", 1))
                    .isInstanceOf(ConfigurationError.class);
        }
    }

    @Test
    void aSeriesStartingBelowOneIsRefused() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> work.numbering().start(INVOICES, 0)).isInstanceOf(ConfigurationError.class);
        }
    }

    @Test
    void openingASeriesTwiceIsRefused() {
        try (UnitOfWork work = opened().unitOfWork()) {
            assertThatThrownBy(() -> work.numbering().start(INVOICES, 7000)).isInstanceOf(ConfigurationError.class);
        }
    }

    @Test
    void aSeriesNeverOpenedCannotBeRead() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> work.numbering().peek(INVOICES)).isInstanceOf(ConfigurationError.class);
        }
    }

    @Test
    void aSeriesNeverOpenedCannotBeTakenFrom() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> work.numbering().take(INVOICES)).isInstanceOf(ConfigurationError.class);
        }
    }

    @Test
    void numbersComeOutOneAfterAnother() {
        Database books = opened();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.numbering().take(INVOICES)).isEqualTo(5001);
            assertThat(work.numbering().take(INVOICES)).isEqualTo(5002);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.numbering().take(INVOICES)).isEqualTo(5003);
        }
    }

    @Test
    void twoSeriesDoNotInterfere() {
        try (UnitOfWork work = opened().unitOfWork()) {
            assertThat(work.numbering().take(INVOICES)).isEqualTo(5001);
            assertThat(work.numbering().take(STATEMENTS)).isEqualTo(9001);
            assertThat(work.numbering().take(INVOICES)).isEqualTo(5002);
            work.commit();
        }
    }

    @Test
    void aRunOfNumbersComesOutTogether() {
        Database books = opened();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.numbering().takeMany(STATEMENTS, 3)).isEqualTo(List.of(9001, 9002, 9003));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.numbering().peek(STATEMENTS)).isEqualTo(9004);
        }
    }

    @Test
    void askingForNoNumbersIsRefused() {
        try (UnitOfWork work = opened().unitOfWork()) {
            assertThatThrownBy(() -> work.numbering().takeMany(INVOICES, 0)).isInstanceOf(ConfigurationError.class);
        }
    }

    @Test
    void aNumberTakenAndRolledBackIsGivenUp() {
        Database books = opened();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.numbering().take(INVOICES)).isEqualTo(5001);
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.numbering().take(INVOICES)).isEqualTo(5001);
        }
    }

    @Test
    void aFailedBlockLeavesNoGap() {
        Database books = opened();
        assertThatThrownBy(() -> {
                    try (UnitOfWork work = books.unitOfWork()) {
                        work.numbering().take(INVOICES);
                        throw new ConfigurationError("the close blew up half way");
                    }
                })
                .isInstanceOf(ConfigurationError.class);
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.numbering().peek(INVOICES)).isEqualTo(5001);
        }
    }

    @Test
    void theOpenSeriesComeBackInOrder() {
        try (UnitOfWork work = opened().unitOfWork()) {
            assertThat(work.numbering().openSeries()).isEqualTo(List.of(INVOICES, STATEMENTS));
        }
    }

    @Test
    void aCounterBelowOneIsRefusedByTheTable() {
        Database books = empty();
        assertThatThrownBy(() -> {
                    try (UnitOfWork work = books.unitOfWork()) {
                        work.session().persist(new CounterRow(INVOICES, 0));
                        work.commit();
                    }
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void peekingDoesNotTakeANumber() {
        try (UnitOfWork work = opened().unitOfWork()) {
            work.numbering().peek(INVOICES);
            work.numbering().peek(INVOICES);
            assertThat(work.numbering().take(INVOICES)).isEqualTo(5001);
        }
    }
}
