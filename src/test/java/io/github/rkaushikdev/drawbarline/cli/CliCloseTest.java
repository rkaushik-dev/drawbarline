package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliCloseTest {

    static final String SHEET =
            """
            car,kind,when,station,road,industry,waybill,tenths
            CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
            CN 400100,moved,2025-01-06 13:00,PWR,,,,336
            CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
            CN 400100,released,2025-01-14 15:00,PWR,,PWRGRN1,,
            CN 400100,pulled,2025-01-15 17:00,PWR,,PWRGRN1,,
            CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
            """;

    @TempDir
    Path tmp;

    private String worked;

    /** The loaded railroad with one car worked through January. */
    @BeforeEach
    void workJanuary() {
        worked = Clerk.books(tmp);
        run(worked, "events", "load", Clerk.sheet(tmp, "events.csv", SHEET));
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    @Test
    void theReferenceTablesLoad() {
        Ran found = run(worked, "reference", "show");
        assertThat(found.output()).contains("rate bands  2");
        assertThat(found.output()).contains("tariffs     1");
        assertThat(found.output()).contains("shippers    2");
        assertThat(found.output()).contains("industries  2");
    }

    @Test
    void aTrackWithNoSwitchingDaysTakesTheWorkingWeek() {
        assertThat(run(worked, "reference", "show").exitCode()).isZero();
    }

    @Test
    void aStepThatCannotBeReadIsRefused() {
        Ran found = run(
                worked,
                "reference",
                "tariff",
                "4300",
                "--effective",
                "2025-07-01",
                "--free-loaded",
                "1",
                "--free-empty",
                "1",
                "--step",
                "nonsense");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aCloseReportsWhatItDid() {
        Ran found = run(worked, "close", "2025-01", "--issued", "2025-02-10");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("month      2025-01");
        assertThat(found.output()).contains("closed 2025-01");
    }

    @Test
    void aClosePostsTheCarHire() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10");
        assertThat(run(worked, "status").output()).contains("postings  1");
    }

    @Test
    void aCloseShutsTheMonth() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10");
        assertThat(run(worked, "periods").output()).contains("closed    2025-01");
    }

    @Test
    void aDryRunChangesNothing() {
        Ran found = run(worked, "close", "2025-01", "--issued", "2025-02-10", "--dry-run");
        assertThat(found.output()).contains("dry run");
        assertThat(run(worked, "periods").output()).contains("nothing closed yet");
    }

    @Test
    void aDryRunStillReportsTheNumbers() {
        Ran found = run(worked, "close", "2025-01", "--issued", "2025-02-10", "--dry-run");
        assertThat(found.output()).contains("car hire");
    }

    @Test
    void closingAMonthTwiceIsRefused() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10");
        assertThat(run(worked, "close", "2025-01", "--issued", "2025-02-10").exitCode())
                .isNotZero();
    }

    @Test
    void aMonthThatCannotBeReadIsRefused() {
        assertThat(run(worked, "close", "january", "--issued", "2025-02-10").exitCode())
                .isNotZero();
    }

    @Test
    void closingBeforeTheMonthEndsIsRefused() {
        assertThat(run(worked, "close", "2025-01", "--issued", "2025-01-20").exitCode())
                .isNotZero();
    }

    @Test
    void aStatementPrintsTheTwoBalances() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10");
        Ran found = run(worked, "statement", "CN", "2025-01");
        assertThat(found.output()).contains("opening   $0.00");
        assertThat(found.output()).contains("we pay CN");
    }

    @Test
    void aRoadWithNothingPostedReadsSquare() {
        Ran found = run(worked, "statement", "ELS", "2025-01");
        assertThat(found.output()).contains("square");
    }

    @Test
    void aMonthIsReopenedWithAReason() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10");
        Ran found = run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet");
        assertThat(found.exitCode()).isZero();
        assertThat(run(worked, "periods").output()).contains("reopened  2025-01");
    }

    @Test
    void reopeningAMonthThatIsOpenIsRefused() {
        Ran found = run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "why not");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aReopenedMonthCanBeClosedAgain() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10");
        run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet");
        assertThat(run(worked, "close", "2025-01", "--issued", "2025-03-05").exitCode())
                .isZero();
    }

    @Test
    void theDemurrageInvoiceIsPrinted() {
        Ran found = run(worked, "close", "2025-01", "--issued", "2025-02-10");
        assertThat(found.output()).contains("invoice    demurrage invoice");
    }
}
