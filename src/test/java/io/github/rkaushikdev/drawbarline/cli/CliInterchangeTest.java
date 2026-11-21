package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliInterchangeTest {

    @TempDir
    Path tmp;

    private String books;

    /** A record of one car, in the layout the connections send. */
    static String aRecord(String kind, String number, String stamp, String clock) {
        return line(
                String.format(Locale.ROOT, "IC%s%-4s%s%s%s041207%05d%-12s", kind, "CN", number, stamp, clock, 0, ""));
    }

    static String aRecord() {
        return aRecord("RC", "400100", "20250106", "0700");
    }

    static String aRecordOfKind(String kind) {
        return aRecord(kind, "400100", "20250106", "0700");
    }

    static String aRecordFor(String number) {
        return aRecord("RC", number, "20250106", "0700");
    }

    /** A whole file from CN at Escanaba: the header, the records and a trailer that counts them. */
    static String aFile(String... records) {
        List<String> lines = new ArrayList<>();
        lines.add(line(String.format(Locale.ROOT, "HD%-4s%-4s20250106%04d", "CN", "ESC", 41)));
        lines.addAll(List.of(records));
        lines.add(line(String.format(Locale.ROOT, "TR%06d", records.length)));
        return String.join("\n", lines) + "\n";
    }

    private static String line(String text) {
        return String.format(Locale.ROOT, "%-80s", text);
    }

    @BeforeEach
    void fileTwoCars() {
        books = tmp.resolve("books.db").toString();
        run(books, "init");
        run(books, Clerk.carArgs("CN 400100"));
        run(books, Clerk.carArgs("CN 400101"));
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    private static Ran run(String database, List<String> args) {
        return Clerk.run(database, args.toArray(String[]::new));
    }

    private String write(String name, String text) throws IOException {
        Path path = tmp.resolve(name);
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path.toString();
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            found++;
        }
        return found;
    }

    @Test
    void aCleanSheetIsTaken() throws IOException {
        String path = write("in.txt", aFile(aRecord()));
        Ran found = run(books, "interchange", "receive", path);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("took 1 records");
    }

    @Test
    void theRecordsReachTheLog() throws IOException {
        String path = write("in.txt", aFile(aRecord()));
        run(books, "interchange", "receive", path);
        assertThat(run(books, "status").output()).contains("events    1");
    }

    @Test
    void theSheetNamesTheRoadAndJunction() throws IOException {
        String path = write("in.txt", aFile(aRecord()));
        Ran found = run(books, "interchange", "receive", path);
        assertThat(found.output()).contains("from CN at ESC");
    }

    @Test
    void aSheetWithABadLineIsRefused() throws IOException {
        String path = write("in.txt", aFile(aRecord(), aRecordOfKind("ZZ")));
        Ran found = run(books, "interchange", "receive", path);
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("did not read cleanly");
    }

    @Test
    void aRefusedSheetRecordsNothing() throws IOException {
        String path = write("in.txt", aFile(aRecord(), aRecordOfKind("ZZ")));
        run(books, "interchange", "receive", path);
        assertThat(run(books, "status").output()).contains("events    0");
    }

    @Test
    void theGoodHalfCanBeTakenOnPurpose() throws IOException {
        String path = write("in.txt", aFile(aRecord(), aRecordOfKind("ZZ"), aRecordFor("400101")));
        Ran found = run(books, "interchange", "receive", path, "--partial");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("took 2 records");
    }

    @Test
    void aSheetWhoseTrailerDisagreesIsRefused() throws IOException {
        String text = aFile(aRecord()).replace("TR000001", "TR000009");
        String path = write("in.txt", text);
        Ran found = run(books, "interchange", "receive", path);
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("trailer says 9");
    }

    @Test
    void aSheetWithNoHeaderIsRefused() throws IOException {
        String path = write("in.txt", aRecord() + "\n");
        Ran found = run(books, "interchange", "receive", path);
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aRecordForACarNobodyFiledIsRefused() throws IOException {
        String path = write("in.txt", aFile(aRecordFor("999999")));
        Ran found = run(books, "interchange", "receive", path);
        assertThat(found.exitCode()).isNotZero();
        assertThat(run(books, "status").output()).contains("events    0");
    }

    @Test
    void aFileThatDoesNotExistIsRefused() {
        assertThat(run(
                                books,
                                "interchange",
                                "receive",
                                tmp.resolve("nowhere.txt").toString())
                        .exitCode())
                .isNotZero();
    }

    @Test
    void aDayOfInterchangeIsWrittenBack() throws IOException {
        String path = write("in.txt", aFile(aRecord(), aRecordFor("400101")));
        run(books, "interchange", "receive", path);
        Ran found = run(books, "interchange", "send", "--road", "CN", "--junction", "ESC", "--day", "2025-01-06");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).startsWith("HDCN");
        assertThat(count(found.output(), "IC")).isEqualTo(2);
    }

    @Test
    void theWrittenSheetReadsBack() throws IOException {
        String path = write("in.txt", aFile(aRecord()));
        run(books, "interchange", "receive", path);
        Path out = tmp.resolve("out.txt");
        Ran found = run(
                books,
                "interchange",
                "send",
                "--road",
                "CN",
                "--junction",
                "ESC",
                "--day",
                "2025-01-06",
                "--out",
                out.toString());
        assertThat(found.output()).contains("wrote 1 records");
        assertThat(Files.readString(out, StandardCharsets.UTF_8)).startsWith("HDCN");
    }

    @Test
    void aDayWithNoInterchangeWritesAnEmptySheet() {
        Ran found = run(books, "interchange", "send", "--road", "CN", "--junction", "ESC", "--day", "2025-02-06");
        assertThat(found.output()).contains("TR000000");
    }

    @Test
    void anotherRoadGetsNoneOfOurRecords() throws IOException {
        String path = write("in.txt", aFile(aRecord()));
        run(books, "interchange", "receive", path);
        Ran found = run(books, "interchange", "send", "--road", "ELS", "--junction", "ESC", "--day", "2025-01-06");
        assertThat(found.output()).contains("TR000000");
    }

    @Test
    void aDayThatCannotBeReadIsRefused() {
        Ran found = run(books, "interchange", "send", "--road", "CN", "--junction", "ESC", "--day", "yesterday");
        assertThat(found.exitCode()).isNotZero();
    }
}
