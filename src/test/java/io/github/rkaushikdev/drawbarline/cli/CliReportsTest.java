package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliReportsTest {

    @TempDir
    Path tmp;

    /** The loaded railroad with one car worked through January. */
    private String worked() {
        String books = Clerk.books(tmp);
        run(books, "events", "load", Clerk.sheet(tmp, "events.csv", CliCloseTest.SHEET));
        return books;
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    @Test
    void theCarHireReportPrints() {
        Ran found = run(worked(), "report", "carhire", "2025-01");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("CAR HIRE LIABILITY");
        assertThat(found.output()).contains("CN");
    }

    @Test
    void theCarHireDetailPrintsForOneRoad() {
        Ran found = run(worked(), "report", "carhire", "2025-01", "--road", "CN");
        assertThat(found.output()).contains("CAR HIRE DETAIL CN");
        assertThat(found.output()).contains("CN 400100");
    }

    @Test
    void aMonthThatCannotBeReadIsRefused() {
        assertThat(run(worked(), "report", "carhire", "january").exitCode()).isNotZero();
    }

    @Test
    void theUtilisationReportPrints() {
        Ran found = run(worked(), "report", "utilisation", "2025-01");
        assertThat(found.output()).contains("FLEET UTILISATION");
        assertThat(found.output()).contains("CN 400100");
    }

    @Test
    void theDemurrageReportPrintsCarByCar() {
        Ran found = run(worked(), "report", "demurrage", "2025-01");
        assertThat(found.output()).contains("DEMURRAGE DETENTION");
        assertThat(found.output()).contains("PWRGRN1");
    }

    @Test
    void theDemurrageReportSummarisesByShipper() {
        Ran found = run(worked(), "report", "demurrage", "2025-01", "--by-shipper");
        assertThat(found.output()).contains("DEMURRAGE BY SHIPPER");
        assertThat(found.output()).contains("Powers Grain");
    }

    @Test
    void theInterchangeReportPrints() {
        Ran found = run(worked(), "report", "interchange", "2025-01");
        assertThat(found.output()).contains("INTERCHANGE BALANCE");
        assertThat(found.output()).contains("ESC");
        assertThat(found.output()).contains("MRN");
    }

    @Test
    void theOnHandReportPrints() {
        Ran found = run(worked(), "report", "onhand", "--at", "2025-01-10");
        assertThat(found.output()).contains("CARS ON HAND");
        assertThat(found.output()).contains("CN 400100");
    }

    @Test
    void aCarAlreadyDeliveredIsNotOnHand() {
        Ran found = run(worked(), "report", "onhand", "--at", "2025-01-20");
        assertThat(found.output()).contains("0 cars");
    }

    @Test
    void theOnHandReportDefaultsToNow() {
        Ran found = run(worked(), "report", "onhand");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("CARS ON HAND");
    }

    @Test
    void theAgeReportPrints() {
        Ran found = run(worked(), "report", "age", "--on", "2025-07-01");
        assertThat(found.output()).contains("FLEET AGE PROFILE");
        assertThat(found.output()).contains("all cars");
    }

    @Test
    void theAgeReportDefaultsToToday() {
        assertThat(run(worked(), "report", "age").exitCode()).isZero();
    }

    @Test
    void aDateThatCannotBeReadIsRefused() {
        assertThat(run(worked(), "report", "age", "--on", "yesterday").exitCode())
                .isNotZero();
    }

    @Test
    void aReportChangesNothing() {
        String worked = worked();
        String before = run(worked, "status").output();
        run(worked, "report", "carhire", "2025-01");
        assertThat(run(worked, "status").output()).isEqualTo(before);
    }

    @Test
    void everyReportRunsOnAnEmptySetOfBooks() {
        String empty = Clerk.empty(tmp);
        run(
                empty,
                "reference",
                "tariff",
                "4200",
                "--effective",
                "2024-07-01",
                "--free-loaded",
                "2",
                "--free-empty",
                "1",
                "--step",
                "0:20000");
        for (List<String> args : List.of(
                List.of("report", "carhire", "2025-01"),
                List.of("report", "utilisation", "2025-01"),
                List.of("report", "demurrage", "2025-01"),
                List.of("report", "interchange", "2025-01"),
                List.of("report", "onhand"),
                List.of("report", "age"))) {
            assertThat(run(empty, args.toArray(String[]::new)).exitCode())
                    .as("%s", args)
                    .isZero();
        }
    }
}
