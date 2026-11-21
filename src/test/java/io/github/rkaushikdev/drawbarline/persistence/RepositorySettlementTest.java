package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.models.ClosedPeriodRow;
import io.github.rkaushikdev.drawbarline.persistence.models.PostingRow;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.PeriodRegister;
import io.github.rkaushikdev.drawbarline.settlement.Posting;
import io.github.rkaushikdev.drawbarline.settlement.PostingKind;
import io.github.rkaushikdev.drawbarline.settlement.Reopening;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositorySettlementTest {

    private static final ReportingMark CN = new ReportingMark("CN");
    private static final ReportingMark ELS = new ReportingMark("ELS");
    private static final Period JANUARY = new Period(2025, 1);
    private static final Period FEBRUARY = new Period(2025, 2);
    private static final LocalDate CLOSED_ON = LocalDate.of(2025, 2, 10);

    private Database books;

    @BeforeEach
    void freshBooks() {
        books = TestDatabase.fresh();
    }

    private static Posting aPosting() {
        return aPosting(CN, PostingKind.CAR_HIRE, new Money(120000));
    }

    private static Posting aPosting(ReportingMark road, PostingKind kind, Money amount) {
        return new Posting(road, JANUARY, kind, amount, JANUARY.lastDay(), "cn january");
    }

    @Test
    void theSettlementTablesAreOnTheMetadata() {
        assertThat(Database.modelTables()).contains("posting", "closed_period", "reopening");
    }

    @Test
    void aPostingGoesInAndComesBackWhole() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.ledger().post(aPosting());
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.ledger().postings()).containsExactly(aPosting());
        }
    }

    @Test
    void aPostingRowReprNamesTheRoadAndMonth() {
        PostingRow row = new PostingRow(CN, 2025, 1, "CAR_HIRE", new Money(1), LocalDate.of(2025, 1, 31), "");
        assertThat(row).hasToString("<PostingRow CN 2025-01 CAR_HIRE>");
    }

    @Test
    void aMonthStartsOpen() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.periods().isOpen(JANUARY)).isTrue();
        }
    }

    @Test
    void closingAMonthShutsIt() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.periods().close(JANUARY, CLOSED_ON);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.periods().isOpen(JANUARY)).isFalse();
        }
    }

    @Test
    void closingAMonthTwiceIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.periods().close(JANUARY, CLOSED_ON);
            assertThatThrownBy(() -> work.periods().close(JANUARY, LocalDate.of(2025, 2, 11)))
                    .isInstanceOf(SettlementError.class);
        }
    }

    @Test
    void theTableRefusesASecondClosingRow() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> {
                        for (int twice = 0; twice < 2; twice++) {
                            work.session().persist(new ClosedPeriodRow(2025, 1, CLOSED_ON));
                        }
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void aMonthThatIsNotAMonthIsRefusedByTheTable() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new ClosedPeriodRow(2025, 13, CLOSED_ON));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void postingIntoAClosedMonthIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.periods().close(JANUARY, CLOSED_ON);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.ledger().post(aPosting())).isInstanceOf(SettlementError.class);
        }
    }

    @Test
    void reopeningLetsAPostingInAgain() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.periods().close(JANUARY, CLOSED_ON);
            work.periods().reopen(JANUARY, LocalDate.of(2025, 3, 1), "corrected sheet from cn");
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.ledger().post(aPosting()).amount()).isEqualTo(new Money(120000));
        }
    }

    @Test
    void reopeningWithoutAReasonIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.periods().close(JANUARY, CLOSED_ON);
            assertThatThrownBy(() -> work.periods().reopen(JANUARY, LocalDate.of(2025, 3, 1), "   "))
                    .isInstanceOf(SettlementError.class);
        }
    }

    @Test
    void reopeningAMonthThatIsOpenIsRefused() {
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.periods().reopen(JANUARY, LocalDate.of(2025, 3, 1), "a reason"))
                    .isInstanceOf(SettlementError.class);
        }
    }

    @Test
    void aReopeningStaysOnFile() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.periods().close(JANUARY, CLOSED_ON);
            work.periods().reopen(JANUARY, LocalDate.of(2025, 3, 1), "corrected sheet from cn");
            work.periods().close(JANUARY, LocalDate.of(2025, 3, 5));
            work.commit();
        }
        List<Reopening> found;
        try (UnitOfWork work = books.unitOfWork()) {
            found = work.periods().reopenings();
        }
        assertThat(found).containsExactly(new Reopening(JANUARY, LocalDate.of(2025, 3, 1), "corrected sheet from cn"));
    }

    @Test
    void theClosedMonthsComeBackInOrder() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.periods().close(FEBRUARY, LocalDate.of(2025, 3, 10));
            work.periods().close(JANUARY, CLOSED_ON);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.periods().closedPeriods()).containsExactly(JANUARY, FEBRUARY);
        }
    }

    @Test
    void postingsComeBackForOneRoad() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.ledger().post(aPosting());
            work.ledger().post(aPosting(ELS, PostingKind.CAR_HIRE, new Money(-8000)));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.ledger().postings(CN)).hasSize(1);
            assertThat(work.ledger().roads()).containsExactly(CN, ELS);
        }
    }

    @Test
    void theWholeLedgerLoadsBack() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.ledger().post(aPosting());
            work.ledger().post(aPosting(CN, PostingKind.REPAIRS, new Money(-45000)));
            work.ledger().post(aPosting(ELS, PostingKind.CAR_HIRE, new Money(-8000)));
            work.commit();
        }
        Ledger ledger;
        try (UnitOfWork work = books.unitOfWork()) {
            ledger = work.ledger().ledger();
        }
        assertThat(ledger.net()).isEqualTo(Map.of(CN, new Money(75000), ELS, new Money(-8000)));
    }

    @Test
    void aLoadedLedgerHoldsTheClosedMonths() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.ledger().post(aPosting());
            work.periods().close(JANUARY, CLOSED_ON);
            work.commit();
        }
        Ledger ledger;
        try (UnitOfWork work = books.unitOfWork()) {
            ledger = work.ledger().ledger();
        }
        assertThat(ledger.net().get(CN)).isEqualTo(new Money(120000));
        assertThatThrownBy(() -> ledger.post(aPosting())).isInstanceOf(SettlementError.class);
    }

    @Test
    void aClosedMonthStillLoadsItsPostings() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.ledger().post(aPosting());
            work.periods().close(JANUARY, CLOSED_ON);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.ledger().size()).isEqualTo(1);
        }
    }

    @Test
    void thePeriodRegisterLoadsBack() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.periods().close(JANUARY, CLOSED_ON);
            work.commit();
        }
        PeriodRegister register;
        try (UnitOfWork work = books.unitOfWork()) {
            register = work.periods().register();
        }
        assertThat(register.isOpen(JANUARY)).isFalse();
        assertThat(register.isOpen(FEBRUARY)).isTrue();
    }

    @Test
    void aPostingOfAKindNobodyPublishesIsRefusedAtTheRead() {
        try (UnitOfWork work = books.unitOfWork()) {
            work.session().persist(new PostingRow(CN, 2025, 1, "BARTER", new Money(1), JANUARY.lastDay(), ""));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.ledger().postings())
                    .isInstanceOf(ConfigurationError.class)
                    .hasMessageContaining("BARTER");
        }
    }
}
