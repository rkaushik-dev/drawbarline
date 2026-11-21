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

class CliDistributionTest {

    private static final List<String> ORDER = List.of(
            "order",
            "place",
            "1",
            "ESCPAP",
            "--track",
            "ESCPAP1",
            "--kind",
            "L",
            "--cars",
            "2",
            "--wanted",
            "2025-01-14",
            "--on",
            "2025-01-10");

    @TempDir
    Path tmp;

    private String books;

    @BeforeEach
    void copyTheBooks() {
        books = Clerk.books(tmp);
    }

    /** The loaded railroad with three empties standing at Powers. */
    private String stocked() {
        for (String mark : List.of("CN 400100", "CN 400101", "CN 400102")) {
            run(
                    books,
                    "events",
                    "record",
                    mark,
                    "received",
                    "--at",
                    "ESC",
                    "--when",
                    "2025-01-06 07:00",
                    "--road",
                    "CN");
            run(
                    books,
                    "events",
                    "record",
                    mark,
                    "moved",
                    "--at",
                    "PWR",
                    "--when",
                    "2025-01-06 13:00",
                    "--tenths",
                    "336");
        }
        return books;
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
    void anOrderIsTaken() {
        Ran found = run(books, ORDER);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("took order 1 ESCPAP 2 covered hopper");
    }

    @Test
    void anOrderForAShipperNobodyFiledIsRefused() {
        List<String> args = new ArrayList<>(ORDER);
        args.set(3, "NOBODY");
        assertThat(run(books, args).exitCode()).isNotZero();
    }

    @Test
    void anOrderPlacedAfterTheDayItWantsIsRefused() {
        assertThat(run(books, with(ORDER, "2025-01-10", "2025-01-20")).exitCode())
                .isNotZero();
    }

    @Test
    void aKindNobodyPublishesIsRefused() {
        assertThat(run(books, with(ORDER, "L", "Z")).exitCode()).isNotZero();
    }

    @Test
    void anOrderIsCancelled() {
        run(books, ORDER);
        Ran found = run(books, "order", "cancel", "1", "--on", "2025-01-12");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("cancelled order 1");
    }

    @Test
    void cancellingTwiceIsRefused() {
        run(books, ORDER);
        run(books, "order", "cancel", "1", "--on", "2025-01-12");
        assertThat(run(books, "order", "cancel", "1", "--on", "2025-01-13").exitCode())
                .isNotZero();
    }

    @Test
    void theEmptiesReportPrints() {
        Ran found = run(stocked(), "empties", "--at", "2025-01-10");
        assertThat(found.output()).contains("EMPTIES ON HAND");
        assertThat(found.output()).contains("3 empties");
        assertThat(found.output()).contains("PWR");
    }

    @Test
    void aLoadedCarIsNotAnEmpty() {
        run(
                books,
                "events",
                "record",
                "CN 400100",
                "received",
                "--at",
                "ESC",
                "--when",
                "2025-01-06 07:00",
                "--road",
                "CN",
                "--waybill",
                "41207");
        assertThat(run(books, "empties", "--at", "2025-01-10").output()).contains("0 empties");
    }

    @Test
    void theDistributionFillsAnOrder() {
        String stocked = stocked();
        run(stocked, ORDER);
        Ran found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10");
        assertThat(found.output()).contains("CAR DISTRIBUTION");
        assertThat(found.output()).contains("2 cars");
        assertThat(found.output()).contains("every order filled");
    }

    @Test
    void theDistributionNamesTheShipper() {
        String stocked = stocked();
        run(stocked, ORDER);
        Ran found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10");
        assertThat(found.output()).contains("ESCPAP");
    }

    @Test
    void theDistributionReportsTheEmptyMileage() {
        String stocked = stocked();
        run(stocked, ORDER);
        Ran found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10");
        assertThat(found.output()).contains("67.2");
    }

    @Test
    void anOrderBiggerThanTheFleetGoesShort() {
        String stocked = stocked();
        run(stocked, with(ORDER, "2", "5"));
        Ran found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10");
        assertThat(found.output()).contains("orders not filled");
        assertThat(found.output()).contains("1 orders");
    }

    @Test
    void aCancelledOrderIsNotFilled() {
        String stocked = stocked();
        run(stocked, ORDER);
        run(stocked, "order", "cancel", "1", "--on", "2025-01-11");
        Ran found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-12");
        assertThat(found.output()).contains("0 cars");
    }

    @Test
    void aDayWithNoOrdersDistributesNothing() {
        Ran found = run(stocked(), "distribute", "--on", "2025-01-14", "--at", "2025-01-10");
        assertThat(found.output()).contains("0 cars");
        assertThat(found.output()).contains("every order filled");
    }

    @Test
    void aDateThatCannotBeReadIsRefused() {
        assertThat(run(books, "distribute", "--on", "tuesday").exitCode()).isNotZero();
    }

    @Test
    void theDistributionChangesNothing() {
        String stocked = stocked();
        run(stocked, ORDER);
        String before = run(stocked, "status").output();
        run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10");
        assertThat(run(stocked, "status").output()).isEqualTo(before);
    }

    @Test
    void everyLineFitsThePaper() {
        String stocked = stocked();
        run(stocked, ORDER);
        Ran found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10");
        for (String line : found.output().split("\n", -1)) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }
}
