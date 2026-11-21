package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliAuditTest {

    private static final String SHEET =
            """
            car,kind,when,station,road,industry,waybill,tenths
            CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
            CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
            """;

    @TempDir
    Path tmp;

    private String worked;

    /** The loaded railroad with one car worked through January. */
    @BeforeEach
    void workJanuary() {
        worked = Clerk.books(tmp);
        Ran loaded = run(worked, "events", "load", Clerk.sheet(tmp, "events.csv", SHEET));
        assertThat(loaded.exitCode()).isZero();
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    /** The lines of the trail that say something. */
    private static List<String> written(Ran found) {
        return found.lines().stream().filter(line -> !line.isBlank()).toList();
    }

    @Test
    void theTrailStartsEmpty() {
        assertThat(run(worked, "audit").output()).contains("nothing on the trail yet");
    }

    @Test
    void aCloseWritesToTheTrail() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs");
        Ran found = run(worked, "audit");
        assertThat(found.output()).contains("rjs closed a period 2025-01");
    }

    @Test
    void anUnsignedCloseSaysSo() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10");
        assertThat(run(worked, "audit").output()).contains("unsigned closed a period");
    }

    @Test
    void theTrailCarriesWhatTheCloseDid() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs");
        Ran found = run(worked, "audit");
        assertThat(found.output()).contains("postings");
        assertThat(found.output()).contains("car hire");
    }

    @Test
    void aDryRunWritesNothingToTheTrail() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs", "--dry-run");
        assertThat(run(worked, "audit").output()).contains("nothing on the trail yet");
    }

    @Test
    void aReopeningWritesItsReasonToTheTrail() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs");
        run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet from cn", "--by", "mkb");
        Ran found = run(worked, "audit");
        assertThat(found.output()).contains("mkb reopened a period 2025-01");
        assertThat(found.output()).contains("corrected sheet from cn");
    }

    @Test
    void theNewestEntryComesFirst() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs");
        run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet", "--by", "mkb");
        List<String> lines = written(run(worked, "audit"));
        assertThat(lines.get(0)).contains("reopened");
    }

    @Test
    void theTrailCanBeReadForOnePerson() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs");
        run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet", "--by", "mkb");
        Ran found = run(worked, "audit", "--by", "rjs");
        assertThat(found.output()).contains("rjs");
        assertThat(found.output()).doesNotContain("mkb");
    }

    @Test
    void aPersonWhoDidNothingHasNoTrail() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs");
        assertThat(run(worked, "audit", "--by", "abc").output()).contains("nothing on the trail yet");
    }

    @Test
    void theLimitHolds() {
        run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs");
        run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet", "--by", "rjs");
        assertThat(written(run(worked, "audit", "--limit", "1"))).hasSize(1);
    }

    @Test
    void aCloseThatFailsLeavesNoTrail() {
        run(worked, "close", "2025-01", "--issued", "2025-01-20", "--by", "rjs");
        assertThat(run(worked, "audit").output()).contains("nothing on the trail yet");
    }

    @Test
    void aReopeningOfAnOpenMonthLeavesNoTrail() {
        run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "x", "--by", "rjs");
        assertThat(run(worked, "audit").output()).contains("nothing on the trail yet");
    }
}
