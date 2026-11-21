package io.github.rkaushikdev.drawbarline.service;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.demurrage.Invoice;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.Numbering;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.settlement.Books;
import io.github.rkaushikdev.drawbarline.settlement.CloseResult;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.Posting;
import io.github.rkaushikdev.drawbarline.settlement.PostingKind;
import io.github.rkaushikdev.drawbarline.settlement.Statement;
import io.github.rkaushikdev.drawbarline.support.Cars;
import io.github.rkaushikdev.drawbarline.support.Filings;
import io.github.rkaushikdev.drawbarline.support.Moves;
import io.github.rkaushikdev.drawbarline.support.Shippers;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceTest {

    private static final Period JANUARY = new Period(2025, 1);
    private static final LocalDate ISSUED = LocalDate.of(2025, 2, 10);

    /** The filings, the counters and two cars, with nothing moved. */
    private static Database engine() {
        return TestDatabase.seeded(work -> {
            work.numbering().start(Numbering.INVOICES, 5001);
            work.numbering().start(Numbering.STATEMENTS, 9001);
            work.customers().addShipper(Shippers.MILL);
            work.customers().addShipper(Shippers.ELEVATOR);
            work.customers().addIndustry(new Industry("ESCPAP1", "mill house", "ESC", Shippers.MILL, 11));
            work.tariffs()
                    .publish(new Tariff(
                            "4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000))));
            for (Band band : Filings.someBands()) {
                work.rates().file(band);
            }
            for (String mark : List.of("CN 400100", "DBL 104207")) {
                work.fleet().add(Cars.aCar(mark));
            }
        });
    }

    /** The same books with one car taken in, spotted at the mill, released and handed back. */
    private static Database worked() {
        Database books = engine();
        try (UnitOfWork work = books.unitOfWork()) {
            EventLog log = work.events().log();
            Moves.received(log, "CN 400100", 6);
            Moves.moved(log, "CN 400100", 6, 336);
            Moves.placed(log, "CN 400100", 7);
            Moves.released(log, "CN 400100", 14);
            Moves.delivered(log, "CN 400100", 16);
            for (CarMark car : log.cars()) {
                CarStream stream = log.stream(car);
                for (Event event : stream) {
                    work.events().record(event);
                }
            }
            work.commit();
        }
        return books;
    }

    private static CloseResult closeAndCommit(Database books) {
        try (UnitOfWork work = books.unitOfWork()) {
            CloseResult result = Service.runClose(work, JANUARY, ISSUED);
            work.commit();
            return result;
        }
    }

    @Test
    void theBooksLoadEveryRegister() {
        Books books;
        try (UnitOfWork work = worked().unitOfWork()) {
            books = Service.booksOf(work);
        }
        assertThat(books.fleet().size()).isEqualTo(2);
        assertThat(books.log().cars()).containsExactly(CarMark.parse("CN 400100"));
        assertThat(books.customers().size()).isEqualTo(1);
    }

    @Test
    void theBooksKnowWhichRoadIsHome() {
        try (UnitOfWork work = worked().unitOfWork()) {
            assertThat(Service.booksOf(work).home()).hasToString("DBL");
        }
    }

    @Test
    void aCloseWritesItsPostings() {
        Database books = worked();
        CloseResult result = closeAndCommit(books);
        assertThat(result.posted()).isEqualTo(1);
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.ledger().postings(Cars.CN)).hasSize(1);
        }
    }

    @Test
    void aCloseShutsTheMonthInTheDatabase() {
        Database books = worked();
        closeAndCommit(books);
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.periods().isOpen(JANUARY)).isFalse();
        }
    }

    @Test
    void closingTheSameMonthTwiceIsRefused() {
        Database books = worked();
        closeAndCommit(books);
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> Service.runClose(work, JANUARY, ISSUED)).isInstanceOf(SettlementError.class);
        }
    }

    @Test
    void aCloseThatIsNotCommittedLeavesNothingBehind() {
        Database books = worked();
        try (UnitOfWork work = books.unitOfWork()) {
            Service.runClose(work, JANUARY, ISSUED);
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.periods().isOpen(JANUARY)).isTrue();
            assertThat(work.ledger().postings()).isEmpty();
        }
    }

    @Test
    void theDocumentsAreNumberedFromTheCounters() {
        CloseResult result = closeAndCommit(worked());
        assertThat(result.invoices().get(0).number()).isEqualTo(5001);
        assertThat(result.statements().get(0).number()).isEqualTo(9001);
    }

    @Test
    void theNextCloseNumbersOnFromThere() {
        Database books = worked();
        closeAndCommit(books);
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.numbering().peek(Numbering.STATEMENTS)).isEqualTo(9002);
        }
    }

    @Test
    void theCarHireReachesTheLedger() {
        Database books = worked();
        CloseResult result = closeAndCommit(books);
        Ledger ledger;
        try (UnitOfWork work = books.unitOfWork()) {
            ledger = work.ledger().ledger();
        }
        assertThat(ledger.account(Cars.CN).byKind(JANUARY).get(PostingKind.CAR_HIRE))
                .isEqualTo(result.carHire());
    }

    @Test
    void aStatementCanBeCutFromTheStoredLedger() {
        Database books = worked();
        closeAndCommit(books);
        Statement statement;
        try (UnitOfWork work = books.unitOfWork()) {
            statement = Statement.cut(work.ledger().ledger(), Cars.CN, JANUARY, 9001, ISSUED);
        }
        assertThat(statement.closing().cents()).isPositive();
    }

    @Test
    void aMonthWithNoTrafficClosesQuietly() {
        CloseResult result = closeAndCommit(engine());
        assertThat(result.posted()).isZero();
        assertThat(result.carHire()).isEqualTo(new Money(0));
    }

    @Test
    void theDemurrageInvoiceReachesTheResult() {
        CloseResult result = closeAndCommit(worked());
        assertThat(result.invoices().stream().map(Invoice::shipper)).containsExactly("ESCPAP");
    }

    @Test
    void anEventRecordedAfterTheCloseDoesNotMoveThePostings() {
        Database books = worked();
        CloseResult first = closeAndCommit(books);
        try (UnitOfWork work = books.unitOfWork()) {
            work.events()
                    .record(Event.builder(EventKind.RECEIVED, CarMark.parse("DBL 104207"), utc(2025, 1, 20, 12), "ESC")
                            .road(Cars.CN)
                            .build());
            work.commit();
        }
        List<Posting> posted;
        try (UnitOfWork work = books.unitOfWork()) {
            posted = work.ledger().postings(Cars.CN);
        }
        assertThat(posted).hasSameSizeAs(first.postings());
    }
}
