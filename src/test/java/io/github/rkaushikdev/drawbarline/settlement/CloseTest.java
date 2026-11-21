package io.github.rkaushikdev.drawbarline.settlement;

import static io.github.rkaushikdev.drawbarline.support.Cars.CN;
import static io.github.rkaushikdev.drawbarline.support.Cars.HOME;
import static io.github.rkaushikdev.drawbarline.support.Moves.badOrdered;
import static io.github.rkaushikdev.drawbarline.support.Moves.delivered;
import static io.github.rkaushikdev.drawbarline.support.Moves.moved;
import static io.github.rkaushikdev.drawbarline.support.Moves.placed;
import static io.github.rkaushikdev.drawbarline.support.Moves.pulled;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static io.github.rkaushikdev.drawbarline.support.Moves.released;
import static io.github.rkaushikdev.drawbarline.support.Moves.repaired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.carhire.Liability;
import io.github.rkaushikdev.drawbarline.carhire.Reclaim;
import io.github.rkaushikdev.drawbarline.demurrage.Detention;
import io.github.rkaushikdev.drawbarline.demurrage.Invoice;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.support.Railroad;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloseTest {

    static final Period JANUARY = new Period(2025, 1);
    static final LocalDate ISSUED = LocalDate.of(2025, 2, 10);

    /** CN 400100 received, moved to Escanaba, placed on the mill track, released, pulled and delivered. */
    static Books aMonth() {
        Books books = Railroad.books("CN 400100", "CN 400101", "DBL 104207");
        received(books.log(), "CN 400100", 6);
        moved(books.log(), "CN 400100", 6, 336);
        placed(books.log(), "CN 400100", 7);
        released(books.log(), "CN 400100", 14);
        pulled(books.log(), "CN 400100", 15);
        delivered(books.log(), "CN 400100", 16);
        return books;
    }

    static CloseResult run(Books books) {
        return Close.closeMonth(books, JANUARY, 5001, 9001, ISSUED);
    }

    static Liability carHire(Books books) {
        return Liability.run(books.fleet(), books.log(), books.rates(), JANUARY.window(), HOME);
    }

    /** CN 400100 received, bad ordered on the 8th, repaired on the 10th and delivered on the 12th. */
    static Books aRepair() {
        Books books = Railroad.books("CN 400100");
        received(books.log(), "CN 400100", 6);
        badOrdered(books.log(), "CN 400100", 8);
        repaired(books.log(), "CN 400100", 10);
        delivered(books.log(), "CN 400100", 12);
        return books;
    }

    @Test
    void theCarHireIsGatheredForTheMonth() {
        Liability found = carHire(aMonth());
        assertThat(found.cars()).isEqualTo(List.of(CarMark.parse("CN 400100")));
    }

    @Test
    void ourOwnCarRaisesNoCarHire() {
        Books books = Railroad.books("DBL 104207");
        received(books.log(), "DBL 104207", 6);
        delivered(books.log(), "DBL 104207", 8);
        assertThat(carHire(books).total()).isEqualTo(new Money(0));
    }

    @Test
    void theClosePostsTheCarHireToTheRoad() {
        Books books = aMonth();
        CloseResult result = run(books);
        assertThat(books.ledger().account(CN).byKind(JANUARY).get(PostingKind.CAR_HIRE))
                .isEqualTo(result.carHire());
    }

    @Test
    void theCloseShutsTheMonth() {
        Books books = aMonth();
        run(books);
        assertThat(books.periods().isOpen(JANUARY)).isFalse();
    }

    @Test
    void closingAMonthThatIsAlreadyShutIsRefused() {
        Books books = aMonth();
        run(books);
        assertThatThrownBy(() -> run(books)).isInstanceOf(SettlementError.class);
    }

    @Test
    void aBadOrderedCarReclaimsItsHours() {
        List<Reclaim> found = Close.reclaimsIn(aRepair(), JANUARY);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).amount().cents()).isGreaterThan(0);
    }

    @Test
    void ourOwnCarReclaimsNothing() {
        Books books = Railroad.books("DBL 104207");
        received(books.log(), "DBL 104207", 6);
        badOrdered(books.log(), "DBL 104207", 8);
        repaired(books.log(), "DBL 104207", 10);
        assertThat(Close.reclaimsIn(books, JANUARY)).isEmpty();
    }

    @Test
    void aReclaimIsPostedAgainstTheCarHire() {
        Books books = aRepair();
        run(books);
        Map<PostingKind, Money> kinds = books.ledger().account(CN).byKind(JANUARY);
        assertThat(kinds.get(PostingKind.RECLAIM).cents()).isLessThan(0);
    }

    @Test
    void aPlacementIsCountedIntoADetention() {
        List<Detention> found = Close.detentionsIn(aMonth(), JANUARY);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).industry()).isEqualTo("ESCPAP1");
    }

    @Test
    void aPlacementWithAWaybillCountsAsLoaded() {
        assertThat(Close.detentionsIn(aMonth(), JANUARY).get(0).loaded()).isTrue();
    }

    @Test
    void aPlacementWithNoWaybillCountsAsEmpty() {
        Books books = Railroad.books("CN 400100");
        received(books.log(), "CN 400100", 6, 12, null);
        placed(books.log(), "CN 400100", 7, "ESCPAP1", null);
        released(books.log(), "CN 400100", 9);
        assertThat(Close.detentionsIn(books, JANUARY).get(0).loaded()).isFalse();
    }

    @Test
    void aPlacementOutsideTheMonthIsLeftOut() {
        Books books = Railroad.books("CN 400100");
        received(books.log(), "CN 400100", 6);
        assertThat(Close.detentionsIn(books, JANUARY)).isEmpty();
    }

    @Test
    void theInvoicesComeOutOneAShipper() {
        Books books = aMonth();
        List<Detention> detentions = Close.detentionsIn(books, JANUARY);
        List<Invoice> found = Close.invoicesFrom(books, JANUARY, detentions, 5001);
        assertThat(found.stream().map(Invoice::shipper)).containsExactly("ESCPAP");
    }

    @Test
    void twoShippersGetTheirOwnInvoicesNumberedInOrder() {
        Books books = Railroad.books("CN 400100", "CN 400101");
        received(books.log(), "CN 400100", 6);
        placed(books.log(), "CN 400100", 7);
        released(books.log(), "CN 400100", 14);
        received(books.log(), "CN 400101", 6);
        placed(books.log(), "CN 400101", 7, "PWRGRN1");
        released(books.log(), "CN 400101", 14, "PWRGRN1");
        List<Detention> detentions = Close.detentionsIn(books, JANUARY);
        List<Invoice> found = Close.invoicesFrom(books, JANUARY, detentions, 5001);
        assertThat(found)
                .extracting(Invoice::number, Invoice::shipper)
                .containsExactly(tuple(5001, "ESCPAP"), tuple(5002, "PWRGRN"));
    }

    @Test
    void theCloseCutsAStatementForEveryRoadWithAnAccount() {
        CloseResult result = run(aMonth());
        assertThat(result.statements().stream().map(Statement::road)).containsExactly(CN);
    }

    @Test
    void theStatementNumberStartsWhereItIsTold() {
        CloseResult result = run(aMonth());
        assertThat(result.statements().get(0).number()).isEqualTo(9001);
    }

    @Test
    void theResultReportsTheThreeTotals() {
        CloseResult result = run(aMonth());
        assertThat(result.carHire().cents()).isGreaterThan(0);
        assertThat(result.reclaimed()).isEqualTo(new Money(0));
        assertThat(result.demurrage().cents()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void theResultCountsWhatItPosted() {
        assertThat(run(aMonth()).posted()).isEqualTo(1);
    }

    @Test
    void aMonthWithNothingInItClosesQuietly() {
        Books books = Railroad.books();
        CloseResult result = run(books);
        assertThat(result.posted()).isEqualTo(0);
        assertThat(result.statements()).isEmpty();
        assertThat(books.periods().isOpen(JANUARY)).isFalse();
    }

    @Test
    void runningTheSameMonthTwiceGivesTheSameCarHire() {
        Money first = carHire(aMonth()).total();
        Money second = carHire(aMonth()).total();
        assertThat(first).isEqualTo(second);
    }
}
