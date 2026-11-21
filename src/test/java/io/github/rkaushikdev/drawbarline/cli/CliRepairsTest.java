package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliRepairsTest {

    private static final List<String> CARD = List.of(
            "repairs",
            "card",
            "88104",
            "DBL 104207",
            "--by",
            "CN",
            "--on",
            "2025-01-14",
            "--at",
            "ESC",
            "--defect",
            "4312:01:1:5",
            "--defect",
            "5104:03");

    private static final List<String> EXCEPTION = List.of(
            "repairs", "except", "88104", "0", "--ground", "depreciation", "--cents", "20000", "--on", "2025-02-03");

    @TempDir
    Path tmp;

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    private static Ran run(String database, List<String> args) {
        return Clerk.run(database, args.toArray(String[]::new));
    }

    /** The loaded railroad with the card filed. */
    private String carded() {
        String books = Clerk.books(tmp);
        run(books, CARD);
        return books;
    }

    @Test
    void thePriceMasterLoads() {
        String books = Clerk.empty(tmp);
        assertThat(run(books, "repairs", "price", tmp.resolve("nonexistent").toString())
                        .exitCode())
                .isNotZero();
        Ran found = run(books, "repairs", "price", Clerk.sheet(tmp, "prices.csv", Clerk.PRICES));
        assertThat(found.output()).contains("priced 2 jobs");
    }

    @Test
    void aCardIsFiled() {
        Ran found = run(Clerk.books(tmp), CARD);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("filed card 88104");
    }

    @Test
    void aCardShowsItsMoney() {
        Ran found = run(carded(), "repairs", "show", "88104");
        assertThat(found.output()).contains("total      $2,021.50");
        assertThat(found.output()).contains("to owner   $1,129.00");
        assertThat(found.output()).contains("their cost $892.50");
    }

    @Test
    void aCardShowsWhoAnswersForEachLine() {
        Ran found = run(carded(), "repairs", "show", "88104");
        assertThat(found.output()).contains("to owner");
        assertThat(found.output()).contains("to handling line");
    }

    @Test
    void aDefectThatCannotBeReadIsRefused() {
        List<String> bad = new ArrayList<>(CARD.subList(0, CARD.size() - 1));
        bad.add("nonsense");
        assertThat(run(Clerk.books(tmp), bad).exitCode()).isNotZero();
    }

    @Test
    void aCardForACarNobodyFiledIsRefused() {
        List<String> bad = new ArrayList<>(CARD);
        bad.set(3, "WC 000001");
        assertThat(run(Clerk.books(tmp), bad).exitCode()).isNotZero();
    }

    @Test
    void theSameCardTwiceIsRefused() {
        assertThat(run(carded(), CARD).exitCode()).isNotZero();
    }

    @Test
    void aCardNobodyFiledCannotBeShown() {
        assertThat(run(Clerk.books(tmp), "repairs", "show", "99999").exitCode()).isNotZero();
    }

    @Test
    void anExceptionIsFiled() {
        Ran found = run(carded(), EXCEPTION);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("card 88104 line 0");
    }

    @Test
    void aGroundNobodyPublishesIsRefused() {
        Ran found = run(
                carded(),
                "repairs",
                "except",
                "88104",
                "0",
                "--ground",
                "because",
                "--cents",
                "20000",
                "--on",
                "2025-02-03");
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("no such ground");
    }

    @Test
    void anExceptionOutOfTimeIsRefused() {
        Ran found = run(
                carded(),
                "repairs",
                "except",
                "88104",
                "0",
                "--ground",
                "price",
                "--cents",
                "20000",
                "--on",
                "2025-06-03");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void anExceptionIsAnsweredInFull() {
        String books = carded();
        run(books, EXCEPTION);
        Ran found = run(books, "repairs", "answer", "88104", "0", "--allow", "20000");
        assertThat(found.output()).contains("allowed in full");
    }

    @Test
    void anExceptionIsAnsweredInPart() {
        String books = carded();
        run(books, EXCEPTION);
        Ran found = run(books, "repairs", "answer", "88104", "0", "--allow", "8000");
        assertThat(found.output()).contains("allowed in part");
    }

    @Test
    void anExceptionIsDeclined() {
        String books = carded();
        run(books, EXCEPTION);
        Ran found = run(books, "repairs", "answer", "88104", "0", "--allow", "0");
        assertThat(found.output()).contains("declined");
    }

    @Test
    void answeringSomethingNeverFiledIsRefused() {
        assertThat(run(carded(), "repairs", "answer", "88104", "0", "--allow", "1")
                        .exitCode())
                .isNotZero();
    }

    @Test
    void theBillingReportPrints() {
        Ran found = run(carded(), "report", "repairs", "2025-01");
        assertThat(found.output()).contains("REPAIR BILLING");
        assertThat(found.output()).contains("CN");
    }

    @Test
    void theExceptionReportPrints() {
        String books = carded();
        run(books, EXCEPTION);
        Ran found = run(books, "report", "repairs", "2025-01", "--exceptions");
        assertThat(found.output()).contains("OPEN REPAIR EXCEPTIONS");
        assertThat(found.output()).contains("88104");
    }

    @Test
    void aMonthWithNoCardsReportsNothing() {
        assertThat(run(Clerk.empty(tmp), "report", "repairs", "2025-01").output())
                .contains("0 roads");
    }
}
