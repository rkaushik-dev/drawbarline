package io.github.rkaushikdev.drawbarline.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.interchange.Reconcile;
import io.github.rkaushikdev.drawbarline.interchange.Reconciliation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportReconcileTest {

    private static final ReportingMark CN = new ReportingMark("CN");

    @TempDir
    Path tmp;

    private static Event received(int number, int hour) {
        return Event.builder(EventKind.RECEIVED, CarMark.parse("CN " + number), Clock.utc(2025, 1, 6, hour), "ESC")
                .road(CN)
                .build();
    }

    private static Event received(int number) {
        return received(number, 12);
    }

    private static Event received() {
        return received(400100);
    }

    private static String aRecord(String number, String clock) {
        return line(
                String.format(Locale.ROOT, "IC%s%-4s%s20250106%s041207%05d%-12s", "RC", "CN", number, clock, 0, ""));
    }

    private static String aRecord(String clock) {
        return aRecord("400100", clock);
    }

    private static String aFile(String... records) {
        List<String> lines = new ArrayList<>();
        lines.add(line(String.format(Locale.ROOT, "HD%-4s%-4s20250106%04d", "CN", "ESC", 41)));
        lines.addAll(List.of(records));
        lines.add(line(String.format(Locale.ROOT, "TR%06d", records.length)));
        return String.join("\n", lines) + "\n";
    }

    private static String line(String text) {
        return String.format(Locale.ROOT, "%-80s", text);
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    /** The books with the one CN car on the register. */
    private String books() {
        String database = tmp.resolve("books.db").toString();
        run(database, "init");
        run(database, Clerk.carArgs("CN 400100").toArray(String[]::new));
        return database;
    }

    private String write(String name, String text) throws IOException {
        Path path = tmp.resolve(name);
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path.toString();
    }

    @Test
    void aCleanComparisonReportsAgreement() {
        List<String> lines = ReconcileReport.reconciliationReport(
                Reconcile.compare(List.of(received()), List.of(received())), CN, "in.txt");
        assertThat(lines.get(0)).isEqualTo("INTERCHANGE RECONCILIATION CN");
        assertThat(lines).contains("agreed: 1");
        assertThat(lines).contains("on their record and not ours: none");
    }

    @Test
    void theirRecordsComeBeforeOurs() {
        Reconciliation found = Reconcile.compare(List.of(received(400101)), List.of(received(400102)));
        List<String> lines = ReconcileReport.reconciliationReport(found, CN, "in.txt");
        int theirs = lines.indexOf("on their record and not ours: 1");
        int ours = lines.indexOf("on our record and not theirs: 1");
        assertThat(theirs).isNotNegative();
        assertThat(ours).isNotNegative();
        assertThat(theirs).isLessThan(ours);
    }

    @Test
    void aRecordOnlyTheyHaveIsListed() {
        Reconciliation found = Reconcile.compare(List.of(), List.of(received(400102)));
        List<String> lines = ReconcileReport.reconciliationReport(found, CN, "in.txt");
        assertThat(lines).anyMatch(line -> line.contains("CN 400102"));
    }

    @Test
    void theTimingsComeWorstFirst() {
        Reconciliation found = Reconcile.compare(
                List.of(received(400100, 12), received(400101, 12)),
                List.of(received(400100, 14), received(400101, 20)));
        List<String> lines = ReconcileReport.reconciliationReport(found, CN, "in.txt");
        List<String> rows =
                lines.stream().filter(line -> line.startsWith("CN 4001")).toList();
        assertThat(rows.get(0)).startsWith("CN 400101");
    }

    @Test
    void nothingOutOfTimeSaysNone() {
        List<String> lines = ReconcileReport.reconciliationReport(
                Reconcile.compare(List.of(received()), List.of(received())), CN, "in.txt");
        assertThat(lines).contains("recorded at different times: none");
    }

    @Test
    void aMatchingSheetReconcilesClean() throws IOException {
        String books = books();
        String path = write("in.txt", aFile(aRecord("0700")));
        run(books, "interchange", "receive", path);
        Ran found = run(books, "interchange", "reconcile", path);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("agreed: 1");
    }

    @Test
    void aSheetWeNeverTookShowsAsOnlyTheirs() throws IOException {
        String path = write("in.txt", aFile(aRecord("0700")));
        Ran found = run(books(), "interchange", "reconcile", path);
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("on their record and not ours: 1");
    }

    @Test
    void aClockDifferenceInsideTheToleranceAgrees() throws IOException {
        String books = books();
        run(books, "interchange", "receive", write("ours.txt", aFile(aRecord("0700"))));
        String theirs = write("theirs.txt", aFile(aRecord("0720")));
        assertThat(run(books, "interchange", "reconcile", theirs).exitCode()).isZero();
    }

    @Test
    void aClockDifferencePastTheToleranceIsReported() throws IOException {
        String books = books();
        run(books, "interchange", "receive", write("ours.txt", aFile(aRecord("0700"))));
        String theirs = write("theirs.txt", aFile(aRecord("0900")));
        Ran found = run(books, "interchange", "reconcile", theirs);
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("recorded at different times: 1");
    }

    @Test
    void theToleranceCanBeTightenedOnTheCommandLine() throws IOException {
        String books = books();
        run(books, "interchange", "receive", write("ours.txt", aFile(aRecord("0700"))));
        String theirs = write("theirs.txt", aFile(aRecord("0720")));
        Ran found = run(books, "interchange", "reconcile", theirs, "--tolerance", "5");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aSheetWithNoRecordsIsRefused() throws IOException {
        String path = write("empty.txt", aFile());
        Ran found = run(books(), "interchange", "reconcile", path);
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("no records to compare");
    }

    @Test
    void aSheetWithNoHeaderIsRefused() throws IOException {
        String path = write("bad.txt", aRecord("0700") + "\n");
        assertThat(run(books(), "interchange", "reconcile", path).exitCode()).isNotZero();
    }

    @Test
    void reconcilingChangesNothing() throws IOException {
        String books = books();
        String path = write("in.txt", aFile(aRecord("0700")));
        run(books, "interchange", "receive", path);
        String before = run(books, "status").output();
        run(books, "interchange", "reconcile", path);
        assertThat(run(books, "status").output()).isEqualTo(before);
    }
}
