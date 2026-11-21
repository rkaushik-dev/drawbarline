package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliEventsTest {

    private static final List<String> CAR = List.of(
            "fleet",
            "add",
            "CN 400100",
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

    private static final String SHEET =
            """
            car,kind,when,station,road,industry,waybill,tenths
            CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
            CN 400100,moved,2025-01-06 13:00,PWR,,,,336
            CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
            """;

    private static final String REPEATED = "CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,\n";

    private static final List<String> RECEIVED = List.of(
            "events", "record", "CN 400100", "received", "--at", "ESC", "--when", "2025-01-06 07:00", "--road", "CN");

    @TempDir
    Path tmp;

    private String books;

    @BeforeEach
    void fileOneCar() {
        books = tmp.resolve("books.db").toString();
        run(books, "init");
        run(books, CAR);
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    private static Ran run(String database, List<String> args) {
        return Clerk.run(database, args.toArray(String[]::new));
    }

    private Path sheet(String name, String text) throws IOException {
        Path sheet = tmp.resolve(name);
        Files.writeString(sheet, text, StandardCharsets.UTF_8);
        return sheet;
    }

    @Test
    void anEventIsRecorded() {
        Ran found = run(books, RECEIVED);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("recorded");
    }

    @Test
    void aRecordedEventShowsUpOnTheStream() {
        run(books, RECEIVED);
        Ran found = run(books, "events", "show", "CN 400100");
        assertThat(found.output()).contains("received at ESC CN");
        assertThat(found.output()).contains("-> CN 400100 at ESC");
    }

    @Test
    void theLocalReadingIsReadAtTheStation() {
        run(books, RECEIVED);
        assertThat(run(books, "events", "show", "CN 400100").output()).contains("2025-01-06 12:00Z");
    }

    @Test
    void anInterchangeWithoutARoadIsRefused() {
        Ran found = run(books, RECEIVED.subList(0, 8));
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aReadingThatNeverHappenedIsRefused() {
        Ran found = run(
                books,
                "events",
                "record",
                "CN 400100",
                "received",
                "--at",
                "PWR",
                "--when",
                "2025-03-09 02:30",
                "--road",
                "CN");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void theAmbiguousHourNeedsTheFlag() {
        Ran first = run(
                books,
                "events",
                "record",
                "CN 400100",
                "received",
                "--at",
                "PWR",
                "--when",
                "2025-11-02 01:30",
                "--road",
                "CN");
        assertThat(first.exitCode()).isZero();
        Ran second = run(
                books,
                "events",
                "record",
                "CN 400100",
                "held",
                "--at",
                "PWR",
                "--when",
                "2025-11-02 01:30",
                "--second-pass");
        assertThat(second.exitCode()).isZero();
    }

    @Test
    void aKindNobodyPublishesIsRefused() {
        Ran found = run(books, "events", "record", "CN 400100", "shunted", "--at", "ESC", "--when", "2025-01-06 07:00");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void theSameEventTwiceIsRefused() {
        run(books, RECEIVED);
        assertThat(run(books, RECEIVED).exitCode()).isNotZero();
    }

    @Test
    void aSheetLoadsInOneGo() throws IOException {
        Path sheet = sheet("interchange.csv", SHEET);
        Ran found = run(books, "events", "load", sheet.toString());
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("took 3 events");
    }

    @Test
    void aLoadedSheetReplays() throws IOException {
        Path sheet = sheet("interchange.csv", SHEET);
        run(books, "events", "load", sheet.toString());
        Ran found = run(books, "events", "show", "CN 400100");
        assertThat(found.output()).contains("-> CN 400100 at PWRGRN1");
    }

    @Test
    void aSheetThatRepeatsALineNamesTheLine() throws IOException {
        Path sheet = sheet("bad.csv", SHEET + REPEATED);
        Ran found = run(books, "events", "load", sheet.toString());
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("line 5");
    }

    @Test
    void aSheetThatFailsLeavesNothingBehind() throws IOException {
        Path sheet = sheet("bad.csv", SHEET + REPEATED);
        run(books, "events", "load", sheet.toString());
        assertThat(run(books, "status").output()).contains("cars      1");
        assertThat(run(books, "status").output()).contains("events    0");
    }

    @Test
    void aSheetMissingAColumnIsRefused() throws IOException {
        Path sheet = sheet("short.csv", "car,kind\nCN 400100,received\n");
        Ran found = run(books, "events", "load", sheet.toString());
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("missing");
    }

    @Test
    void aSheetThatDoesNotExistIsRefused() {
        assertThat(run(books, "events", "load", tmp.resolve("nowhere.csv").toString())
                        .exitCode())
                .isNotZero();
    }

    @Test
    void aStreamForACarWithNothingOnItReadsOffLine() {
        Ran found = run(books, "events", "show", "CN 400100");
        assertThat(found.output()).contains("off line");
    }

    @Test
    void theStatusCountsTheEvents() throws IOException {
        Path sheet = sheet("interchange.csv", SHEET);
        run(books, "events", "load", sheet.toString());
        assertThat(run(books, "status").output()).contains("events    3");
    }
}
