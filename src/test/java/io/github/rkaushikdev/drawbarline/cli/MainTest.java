package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The house rules every command keeps: what the exit code means, where a refusal is written, and
 * that every command and every group answers for itself when asked.
 */
class MainTest {

    @TempDir
    Path tmp;

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    @Test
    void aRefusalAboutTheBooksExitsOneWithTheRefusalOnStandardError() {
        Ran found = run(Clerk.empty(tmp), "fleet", "show", "WC 1");
        assertThat(found.exitCode()).isEqualTo(Main.REFUSED);
        assertThat(found.out()).isEmpty();
        assertThat(found.err()).startsWith("error: ").contains("car='WC 000001'");
    }

    @Test
    void anArgumentThatCannotBeReadExitsTwo() {
        Ran found = run(Clerk.empty(tmp), "report", "age", "--on", "yesterday");
        assertThat(found.exitCode()).isEqualTo(Main.MISUSED);
        assertThat(found.err()).isEqualTo("error: 'yesterday' is not a date, write it as yyyy-mm-dd\n");
    }

    @Test
    void aMissingOptionOrAnUnknownCommandExitsTwo() {
        String books = Clerk.empty(tmp);
        Ran missing = run(books, "close", "2025-01");
        assertThat(missing.exitCode()).isEqualTo(Main.MISUSED);
        assertThat(missing.err()).startsWith("error: ").contains("--issued");
        assertThat(run(books, "fleet", "scrap").exitCode()).isEqualTo(Main.MISUSED);
        assertThat(run(books, "fleet", "add", "CN 1", "--light-weight", "heavy").err())
                .contains("'heavy' is not a valid integer.");
    }

    @Test
    void aCheckThatFindsTheBooksWantingExitsOne() {
        Ran found = run(Clerk.empty(tmp), "doctor", "--on", "2025-01-14");
        assertThat(found.exitCode()).isEqualTo(Main.REFUSED);
        assertThat(found.err()).isEqualTo("not ready, see the lines marked bad\n");
    }

    @Test
    void everyGroupAndEveryCommandAnswersForItself() {
        Ran root = Clerk.plain(Map.of());
        assertThat(root.exitCode()).isZero();
        assertThat(root.out()).contains("Car hire, demurrage and interchange settlement.");
        List<String> commands = List.of(
                "agreement",
                "audit",
                "close",
                "deliver",
                "distribute",
                "doctor",
                "embargo",
                "empties",
                "events",
                "export",
                "fleet",
                "init",
                "interchange",
                "order",
                "periods",
                "reference",
                "reopen",
                "repairs",
                "report",
                "serve",
                "settings",
                "statement",
                "status",
                "switchlist",
                "waybill");
        assertThat(root.out()).contains(commands);
        Ran group = Clerk.plain(Map.of(), "report");
        assertThat(group.exitCode()).isZero();
        assertThat(group.out()).contains("What the office reads.").contains("carhire", "package", "utilisation");
        Ran help = Clerk.plain(Map.of(), "fleet", "add", "--help");
        assertThat(help.exitCode()).isZero();
        assertThat(help.out()).contains("File an equipment record.").contains("--light-weight");
        Ran version = Clerk.plain(Map.of(), "--version");
        assertThat(version.out()).startsWith("drawbarline, version ");
    }

    @Test
    void anAtSignIsAnArgumentLikeAnyOtherAndTheLastOfARepeatedOptionWins() {
        String books = Clerk.empty(tmp);
        Ran found = run(books, "--log-level", "error", "--log-level", "debug", "fleet", "show", "@cars");
        assertThat(found.exitCode()).isEqualTo(Main.REFUSED);
        assertThat(found.err()).contains("'@cars'");
        assertThat(Clerk.plain(Map.of(), "--log-level", "error", "--log-level", "debug", "settings")
                        .out())
                .contains("log level  debug");
    }

    @Test
    void theReportsNobodyElseRunsPrintFromTheCommandLine() {
        String books = Clerk.books(tmp);
        assertThat(run(books, "report", "daily", "--on", "2025-01-14").out()).contains("MORNING SHEET");
        assertThat(run(books, "report", "forecast", "--at", "2025-01-10", "--days", "3")
                        .out())
                .contains("CAR HIRE FORECAST");
        assertThat(run(books, "report", "annual", "2025").out()).contains("ANNUAL SUMMARY");
        assertThat(run(books, "report", "annual", "2025", "--road", "CN").out()).contains("ANNUAL SUMMARY");
        assertThat(run(books, "report", "shop", "--at", "2025-01-10").out()).contains("BAD ORDER REPORT");
        assertThat(run(books, "report", "balance", "2025-01", "--by-kind").out())
                .contains("POSTINGS BY KIND");
        assertThat(run(books, "report", "package", "2025-01").out()).startsWith("TRIAL BALANCE");
    }
}
