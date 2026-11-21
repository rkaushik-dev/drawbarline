package io.github.rkaushikdev.drawbarline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One month, worked all the way through, from an empty database.
 *
 * <p>Every other test class tests one thing. This works a month the way the office works one,
 * through the command line, and then reads the same month back four different ways and checks the
 * four agree. It is slow by the standards of the rest of the suite and it is the test that would
 * have caught every integration fault this application has actually had.
 *
 * <p>The numbers are not asserted against constants. They are asserted against each other, because
 * the property that matters is not that car hire came to a particular figure, it is that the
 * statement, the trial balance, the export and the report all say the same figure.
 */
class EndToEndTest {

    private static final String INTERCHANGE =
            """
            car,kind,when,station,road,industry,waybill,tenths
            CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
            CN 400100,moved,2025-01-06 13:00,PWR,,,,336
            CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
            CN 400100,released,2025-01-14 15:00,PWR,,PWRGRN1,,
            CN 400100,pulled,2025-01-15 17:00,PWR,,PWRGRN1,,
            CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
            CN 400101,received,2025-01-08 07:00,ESC,CN,,41208,
            CN 400101,bad_ordered,2025-01-09 08:00,ESC,,,,
            CN 400101,repaired,2025-01-12 08:00,ESC,,,,
            CN 400101,delivered,2025-01-20 09:00,MRN,CN,,,
            """;

    private static final List<String> CARD = List.of(
            "repairs",
            "card",
            "88104",
            "CN 400101",
            "--by",
            "CN",
            "--on",
            "2025-01-10",
            "--at",
            "ESC",
            "--defect",
            "4312:01:1:5");

    private static final List<String> CLOSE = List.of("close", "2025-01", "--issued", "2025-02-10");

    @TempDir
    Path tmp;

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    private static Ran run(String database, List<String> args, String... more) {
        List<String> all = new ArrayList<>(args);
        all.addAll(List.of(more));
        return Clerk.run(database, all.toArray(String[]::new));
    }

    /** A month of traffic on the loaded railroad, not yet closed. */
    private String worked() {
        String books = Clerk.books(tmp);
        assertThat(run(books, "events", "load", Clerk.sheet(tmp, "interchange.csv", INTERCHANGE))
                        .exitCode())
                .isZero();
        assertThat(run(books, CARD).exitCode()).isZero();
        return books;
    }

    private static String moneyIn(String text, String after) {
        String line =
                text.lines().filter(one -> one.startsWith(after)).findFirst().orElseThrow();
        List<String> amounts = Arrays.stream(line.split("\\s+"))
                .filter(word -> word.startsWith("$") || word.startsWith("-$"))
                .toList();
        return amounts.get(amounts.size() - 1);
    }

    private static String closingOf(String database) {
        String statement = run(database, "statement", "CN", "2025-01").output();
        return statement
                .lines()
                .filter(one -> one.startsWith("closing"))
                .findFirst()
                .orElseThrow();
    }

    private static String lastWord(String line) {
        String[] words = line.strip().split("\\s+");
        return words[words.length - 1];
    }

    private static JsonNode exported(String database) throws IOException {
        return new ObjectMapper().readTree(run(database, "export", "2025-01").output());
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            found++;
        }
        return found;
    }

    @Test
    void theBooksAreReadyBeforeTheMonthIsWorked() {
        assertThat(run(Clerk.books(tmp), "doctor", "--on", "2025-01-14").exitCode())
                .isZero();
    }

    @Test
    void theMonthLoads() {
        Ran found = run(worked(), "status");
        assertThat(found.output()).contains("events    10");
        assertThat(found.output()).contains("postings  0");
    }

    @Test
    void aDryRunLeavesTheMonthOpen() {
        String worked = worked();
        run(worked, CLOSE, "--dry-run");
        assertThat(run(worked, "periods").output()).contains("nothing closed yet");
    }

    @Test
    void theMonthCloses() {
        Ran found = run(worked(), CLOSE, "--by", "rjs");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("closed 2025-01");
    }

    @Test
    void theCloseIsSigned() {
        String worked = worked();
        run(worked, CLOSE, "--by", "rjs");
        assertThat(run(worked, "audit").output()).contains("rjs closed a period 2025-01");
    }

    @Test
    void theMonthCannotBeClosedTwice() {
        String worked = worked();
        run(worked, CLOSE);
        assertThat(run(worked, CLOSE).exitCode()).isNotZero();
    }

    @Test
    void theTrialBalanceBalances() {
        String worked = worked();
        run(worked, CLOSE);
        Ran found = run(worked, "report", "balance", "2025-01");
        assertThat(found.output().stripTrailing()).endsWith("balances");
    }

    @Test
    void theStatementAndTheTrialBalanceAgree() {
        String worked = worked();
        run(worked, CLOSE);
        String balance = run(worked, "report", "balance", "2025-01").output();
        assertThat(balance).contains(lastWord(closingOf(worked)));
    }

    @Test
    void theExportCarriesTheSameClosingBalance() throws IOException {
        String worked = worked();
        run(worked, CLOSE);
        long cents =
                exported(worked).get("statements").get(0).get("closing_cents").asLong();
        assertThat("$" + String.format(Locale.ROOT, "%,.2f", cents / 100.0)).isEqualTo(lastWord(closingOf(worked)));
    }

    @Test
    void theExportSaysTheMonthIsClosed() throws IOException {
        String worked = worked();
        run(worked, CLOSE);
        JsonNode bundle = exported(worked);
        assertThat(bundle.get("closed").isBoolean()).isTrue();
        assertThat(bundle.get("closed").asBoolean()).isTrue();
        assertThat(bundle.get("balances").isBoolean()).isTrue();
        assertThat(bundle.get("balances").asBoolean()).isTrue();
    }

    @Test
    void theExportCarriesEveryEvent() throws IOException {
        String worked = worked();
        run(worked, CLOSE);
        assertThat(exported(worked).get("events")).hasSize(10);
    }

    @Test
    void theCarHireReportNamesTheOnlyConnection() {
        Ran found = run(worked(), "report", "carhire", "2025-01");
        assertThat(found.output()).contains("CN");
        assertThat(found.output()).contains("all roads");
    }

    @Test
    void theReclaimShowsUpAgainstTheCarHire() {
        Ran found = run(worked(), CLOSE);
        assertThat(moneyIn(found.output(), "reclaimed")).isNotEqualTo("$0.00");
    }

    @Test
    void theBadOrderedCarIsOnTheShopReport() {
        Ran found = run(worked(), "report", "shop", "--at", "2025-01-11");
        assertThat(found.output()).contains("CN 400101");
        assertThat(found.output()).contains("1 down");
    }

    @Test
    void theRepairCardReachesTheBillingReport() {
        Ran found = run(worked(), "report", "repairs", "2025-01");
        assertThat(found.output()).contains("CN");
        assertThat(found.output()).contains("1 roads");
    }

    @Test
    void theDemurrageInvoiceIsCut() {
        Ran found = run(worked(), CLOSE);
        assertThat(found.output()).contains("invoice    demurrage invoice");
        assertThat(found.output()).contains("PWRGRN");
    }

    @Test
    void theClosePackageCarriesEverySection() {
        String worked = worked();
        run(worked, CLOSE);
        Ran found = run(worked, "report", "package", "2025-01");
        for (String heading : List.of(
                "TRIAL BALANCE",
                "CAR HIRE LIABILITY",
                "DEMURRAGE BY SHIPPER",
                "REPAIR BILLING",
                "INTERCHANGE BALANCE",
                "FLEET UTILISATION")) {
            assertThat(found.output()).contains(heading);
        }
    }

    @Test
    void thePackageOpensWithTheTrialBalance() {
        String worked = worked();
        run(worked, CLOSE);
        Ran found = run(worked, "report", "package", "2025-01");
        assertThat(found.output().lines().findFirst()).contains("TRIAL BALANCE");
    }

    @Test
    void aReopenedMonthCanBeWorkedAgain() {
        String worked = worked();
        run(worked, CLOSE, "--by", "rjs");
        run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet from cn", "--by", "mkb");
        assertThat(run(worked, "close", "2025-01", "--issued", "2025-03-05").exitCode())
                .isZero();
        String trail = run(worked, "audit").output();
        assertThat(trail).contains("mkb reopened a period");
        assertThat(count(trail, "closed a period 2025-01")).isEqualTo(2);
    }

    @Test
    void theMonthStillBalancesAfterAReopening() {
        String worked = worked();
        run(worked, CLOSE);
        run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected");
        run(worked, "close", "2025-01", "--issued", "2025-03-05");
        Ran found = run(worked, "report", "balance", "2025-01");
        assertThat(found.output().stripTrailing()).endsWith("balances");
    }

    @Test
    void aCarFiledAfterTheCloseDoesNotMoveIt() {
        String worked = worked();
        run(worked, CLOSE);
        String before = run(worked, "report", "balance", "2025-01").output();
        run(worked, Clerk.carArgs("DBL 104209"));
        assertThat(run(worked, "report", "balance", "2025-01").output()).isEqualTo(before);
    }
}
