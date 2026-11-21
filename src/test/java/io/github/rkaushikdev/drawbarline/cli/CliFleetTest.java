package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliFleetTest {

    static final List<String> CAR = List.of(
            "fleet",
            "add",
            "DBL 104207",
            "--designation",
            "LO",
            "--type-code",
            "L351",
            "--built",
            "2020-06-01",
            "--light-weight",
            "63500",
            "--gross-rail-load",
            "286000",
            "--length",
            "59-6");

    @TempDir
    Path tmp;

    private String books;

    @BeforeEach
    void initTheBooks() {
        books = tmp.resolve("books.db").toString();
        assertThat(run(books, "init").exitCode()).isZero();
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    private static Ran run(String database, List<String> args) {
        return Clerk.run(database, args.toArray(String[]::new));
    }

    private static List<String> with(List<String> args, String from, String to) {
        List<String> changed = new ArrayList<>(args);
        changed.set(changed.indexOf(from), to);
        return changed;
    }

    @Test
    void initBuildsTheDatabase() {
        String database = tmp.resolve("fresh.db").toString();
        Ran found = run(database, "init");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("built");
    }

    @Test
    void initTwiceIsRefused() {
        Ran found = run(books, "init");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void statusReadsAnEmptySetOfBooks() {
        Ran found = run(books, "status");
        assertThat(found.output()).contains("cars      0");
        assertThat(found.output()).contains("closed    0 months");
    }

    @Test
    void aCarIsFiledAndShowsUp() {
        assertThat(run(books, CAR).exitCode()).isZero();
        Ran found = run(books, "fleet", "list");
        assertThat(found.output()).contains("DBL 104207");
        assertThat(found.output()).contains("1 cars");
    }

    @Test
    void theSameCarTwiceIsRefused() {
        run(books, CAR);
        Ran found = run(books, CAR);
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aCarThatCannotBeReadIsRefused() {
        List<String> bad = new ArrayList<>(CAR);
        bad.set(2, "not a car");
        Ran found = run(books, bad);
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aCarWhoseCodesDisagreeIsRefused() {
        assertThat(run(books, with(CAR, "L351", "X202")).exitCode()).isNotZero();
    }

    @Test
    void aCarHeavierThanItsGrossRailLoadIsRefused() {
        assertThat(run(books, with(CAR, "63500", "300000")).exitCode()).isNotZero();
    }

    @Test
    void showPrintsTheStencilAndTheDates() {
        run(books, CAR);
        Ran found = run(books, "fleet", "show", "DBL 104207");
        assertThat(found.output()).contains("DBL 104207-6");
        assertThat(found.output()).contains("2070-06-01");
        assertThat(found.output()).contains("covered hopper");
    }

    @Test
    void showPrintsTheLoadLimit() {
        run(books, CAR);
        assertThat(run(books, "fleet", "show", "DBL 104207").output()).contains("222,500 lb");
    }

    @Test
    void showOnACarNobodyFiledIsRefused() {
        assertThat(run(books, "fleet", "show", "WC 1").exitCode()).isNotZero();
    }

    @Test
    void aRebuiltCarShowsItsRebuild() {
        List<String> rebuilt = new ArrayList<>(CAR);
        rebuilt.addAll(List.of("--rebuilt", "2022-09-01"));
        run(books, rebuilt);
        assertThat(run(books, "fleet", "show", "DBL 104207").output()).contains("rebuilt      2022-09-01");
    }

    @Test
    void theListFiltersByOwner() {
        run(books, CAR);
        List<String> other = new ArrayList<>(CAR);
        other.set(2, "CN 400100");
        run(books, other);
        assertThat(run(books, "fleet", "list", "--owner", "cn").output()).contains("1 cars");
        assertThat(run(books, "fleet", "list").output()).contains("2 cars");
    }

    @Test
    void aCarIsRetired() {
        run(books, CAR);
        assertThat(run(books, "fleet", "retire", "DBL 104207", "--on", "2025-03-31")
                        .exitCode())
                .isZero();
        assertThat(run(books, "fleet", "show", "DBL 104207").output()).contains("retired      2025-03-31");
    }

    @Test
    void retiringTwiceIsRefused() {
        run(books, CAR);
        run(books, "fleet", "retire", "DBL 104207", "--on", "2025-03-31");
        Ran found = run(books, "fleet", "retire", "DBL 104207", "--on", "2025-06-30");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aFilingThatFailsLeavesNothingBehind() {
        run(books, with(CAR, "63500", "300000"));
        assertThat(run(books, "fleet", "list").output()).contains("0 cars");
    }

    @Test
    void theStatusCountsTheCars() {
        run(books, CAR);
        assertThat(run(books, "status").output()).contains("cars      1");
    }

    @Test
    void theVersionIsAvailable() {
        Ran found = run(books, "--version");
        assertThat(found.exitCode()).isZero();
    }
}
