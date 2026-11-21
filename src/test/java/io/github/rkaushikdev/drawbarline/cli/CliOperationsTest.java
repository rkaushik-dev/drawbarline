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

class CliOperationsTest {

    private static final List<String> WAYBILL = List.of(
            "waybill",
            "issue",
            "41207",
            "CN 400100",
            "--dated",
            "2025-01-14",
            "--shipper",
            "PWRGRN",
            "--consignee",
            "ESCPAP",
            "--route",
            "PWR,HRM,BKR,ESC",
            "--commodity",
            "0113710",
            "--commodity-name",
            "wheat",
            "--pounds",
            "198000");

    private static final List<String> EMBARGO = List.of(
            "embargo", "issue", "101", "--effective", "2025-01-06", "--reason", "bridge out", "--station", "HRM");

    @TempDir
    Path tmp;

    private String books;

    @BeforeEach
    void copyTheBooks() {
        books = Clerk.books(tmp);
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    private static Ran run(String database, List<String> args) {
        return Clerk.run(database, args.toArray(String[]::new));
    }

    private static Ran receive(String database, String car) {
        return run(
                database,
                "events",
                "record",
                car,
                "received",
                "--at",
                "ESC",
                "--when",
                "2025-01-06 07:00",
                "--road",
                "CN");
    }

    @Test
    void aWaybillIsIssued() {
        Ran found = run(books, WAYBILL);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("issued wb 41207");
    }

    @Test
    void aWaybillShowsItsRouteAndMileage() {
        run(books, WAYBILL);
        Ran found = run(books, "waybill", "show", "41207");
        assertThat(found.output()).contains("route      PWR HRM BKR ESC");
        assertThat(found.output()).contains("miles      33.6");
    }

    @Test
    void aLoadedWaybillShowsItsLading() {
        run(books, WAYBILL);
        assertThat(run(books, "waybill", "show", "41207").output()).contains("lading     wheat");
    }

    @Test
    void anEmptyWaybillSaysEmpty() {
        run(books, WAYBILL.subList(0, 12));
        assertThat(run(books, "waybill", "show", "41207").output()).contains("lading     empty");
    }

    @Test
    void aLadingWithNoWeightIsRefused() {
        List<String> args = new ArrayList<>(WAYBILL.subList(0, 12));
        args.addAll(List.of("--commodity", "0113710"));
        assertThat(run(books, args).exitCode()).isNotZero();
    }

    @Test
    void aHazardousWaybillSaysSo() {
        List<String> args = new ArrayList<>(WAYBILL);
        args.set(args.indexOf("0113710"), "4930040");
        run(books, args);
        assertThat(run(books, "waybill", "show", "41207").output()).contains("hazardous");
    }

    @Test
    void theSameNumberTwiceIsRefused() {
        run(books, WAYBILL);
        assertThat(run(books, WAYBILL).exitCode()).isNotZero();
    }

    @Test
    void aWaybillNobodyIssuedCannotBeShown() {
        assertThat(run(books, "waybill", "show", "99999").exitCode()).isNotZero();
    }

    @Test
    void anEmbargoIsIssued() {
        Ran found = run(books, EMBARGO);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("issued embargo 101 at HRM");
    }

    @Test
    void anEmbargoThatRestrictsNothingIsRefused() {
        Ran found = run(books, "embargo", "issue", "101", "--effective", "2025-01-06", "--reason", "everything");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void theEmbargoListPrints() {
        run(books, EMBARGO);
        Ran found = run(books, "embargo", "list");
        assertThat(found.output()).contains("EMBARGOES");
        assertThat(found.output()).contains("1 embargoes");
        assertThat(found.output()).contains("open");
    }

    @Test
    void theListCanBeAskedForOneDay() {
        run(books, EMBARGO);
        assertThat(run(books, "embargo", "list", "--on", "2025-01-05").output()).contains("0 embargoes");
    }

    @Test
    void anEmbargoIsLifted() {
        run(books, EMBARGO);
        Ran found = run(books, "embargo", "lift", "101", "--on", "2025-01-20");
        assertThat(found.output()).contains("lifted embargo 101");
        assertThat(run(books, "embargo", "list", "--on", "2025-01-21").output()).contains("0 embargoes");
    }

    @Test
    void liftingSomethingNeverIssuedIsRefused() {
        assertThat(run(books, "embargo", "lift", "101", "--on", "2025-01-20").exitCode())
                .isNotZero();
    }

    @Test
    void anEmptySwitchListSaysNothingToDo() {
        Ran found = run(books, "switchlist", "ESC", "--day", "2025-01-14", "--at", "2025-01-14");
        assertThat(found.output()).contains("nothing to do");
        assertThat(found.output()).contains("worked 0 of 0");
    }

    @Test
    void anOrderPlacesACar() {
        receive(books, "CN 400100");
        Ran found = run(
                books,
                "switchlist",
                "ESC",
                "--day",
                "2025-01-14",
                "--at",
                "2025-01-14",
                "--order",
                "CN 400100=ESCPAP1");
        assertThat(found.output()).contains("place CN 400100 ESCPAP1");
        assertThat(found.output()).contains("worked 1 of 1");
    }

    @Test
    void aFullTrackTakesAConstructivePlacement() {
        for (String number : List.of("CN 400100", "CN 400101", "CN 400102")) {
            receive(books, number);
        }
        for (String number : List.of("CN 400100", "CN 400101")) {
            run(
                    books,
                    "events",
                    "record",
                    number,
                    "placed",
                    "--at",
                    "ESC",
                    "--when",
                    "2025-01-07 08:00",
                    "--industry",
                    "ESCPAP1");
        }
        Ran found = run(
                books,
                "switchlist",
                "ESC",
                "--day",
                "2025-01-14",
                "--at",
                "2025-01-14",
                "--order",
                "CN 400102=ESCPAP1");
        assertThat(found.output()).contains("constructive CN 400102 ESCPAP1");
        assertThat(found.output()).contains("2 on hand");
    }

    @Test
    void anOrderThatCannotBeReadIsRefused() {
        Ran found = run(books, "switchlist", "ESC", "--day", "2025-01-14", "--order", "nonsense");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void anOrderForACarNotOnOurRailIsRefused() {
        Ran found = run(
                books,
                "switchlist",
                "ESC",
                "--day",
                "2025-01-14",
                "--at",
                "2025-01-14",
                "--order",
                "CN 400100=ESCPAP1");
        assertThat(found.exitCode()).isNotZero();
    }
}
