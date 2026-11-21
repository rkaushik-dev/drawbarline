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

class CliAgreementsTest {

    private static final List<String> SIGN = List.of(
            "agreement",
            "sign",
            "CN",
            "ESC",
            "--effective",
            "2024-07-01",
            "--kinds",
            "LX",
            "--cap",
            "2",
            "--opens",
            "07:00",
            "--closes",
            "19:00");

    private static final List<String> RECEIVED = List.of(
            "events", "record", "CN 400100", "received", "--at", "ESC", "--when", "2025-01-06 07:00", "--road", "CN");

    @TempDir
    Path tmp;

    private String books;

    @BeforeEach
    void copyTheBooks() {
        books = Clerk.books(tmp);
    }

    /** The loaded railroad with the CN agreement at Escanaba signed and a CN car received there. */
    private String signed() {
        run(books, SIGN);
        run(books, RECEIVED);
        return books;
    }

    private static Ran run(String database, String... args) {
        return Clerk.run(database, args);
    }

    private static Ran run(String database, List<String> args) {
        return Clerk.run(database, args.toArray(String[]::new));
    }

    private static Ran deliver(String database, String when, String... more) {
        List<String> args =
                new ArrayList<>(List.of("deliver", "CN 400100", "--to", "CN", "--at", "ESC", "--when", when));
        args.addAll(List.of(more));
        return run(database, args);
    }

    @Test
    void anAgreementIsSigned() {
        Ran found = run(books, SIGN);
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("signed ESC/CN 07:00 to 19:00 cap 2");
    }

    @Test
    void aKindNobodyPublishesIsRefused() {
        List<String> args = new ArrayList<>(SIGN);
        args.set(args.indexOf("LX"), "LZ");
        assertThat(run(books, args).exitCode()).isNotZero();
    }

    @Test
    void aTimeThatCannotBeReadIsRefused() {
        List<String> args = new ArrayList<>(SIGN);
        args.set(args.indexOf("07:00"), "morning");
        assertThat(run(books, args).exitCode()).isNotZero();
    }

    @Test
    void aJunctionThatClosesFirstIsRefused() {
        List<String> args = new ArrayList<>(SIGN);
        args.set(args.indexOf("07:00"), "19:00");
        args.set(args.lastIndexOf("19:00"), "07:00");
        assertThat(run(books, args).exitCode()).isNotZero();
    }

    @Test
    void theSameAgreementTwiceIsRefused() {
        run(books, SIGN);
        assertThat(run(books, SIGN).exitCode()).isNotZero();
    }

    @Test
    void theAgreementListPrints() {
        Ran found = run(signed(), "agreement", "list");
        assertThat(found.output()).contains("INTERCHANGE AGREEMENTS");
        assertThat(found.output()).contains("1 agreements");
        assertThat(found.output()).contains("ESC");
    }

    @Test
    void theListCanBeAskedForOneDay() {
        assertThat(run(signed(), "agreement", "list", "--on", "2024-01-01").output())
                .contains("0 agreements");
    }

    @Test
    void signingWritesToTheAuditTrail() {
        List<String> args = new ArrayList<>(SIGN);
        args.addAll(List.of("--by", "rjs"));
        run(books, args);
        assertThat(run(books, "audit").output()).contains("rjs signed an interchange agreement");
    }

    @Test
    void aSoundCarGoesAcross() {
        Ran found = deliver(signed(), "2025-01-14 09:00");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("goes to CN at ESC");
    }

    @Test
    void aCarOfferedWhenTheJunctionIsShutIsHeld() {
        Ran found = deliver(signed(), "2025-01-14 22:00");
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("closed at 22:00");
    }

    @Test
    void aKindTheRoadDoesNotTakeIsHeld() {
        List<String> args = new ArrayList<>(SIGN);
        args.set(args.indexOf("LX"), "T");
        run(books, args);
        run(books, RECEIVED);
        Ran found = deliver(books, "2025-01-14 09:00");
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("does not take a covered hopper");
    }

    @Test
    void anEmbargoAtTheJunctionHoldsTheCar() {
        String signed = signed();
        run(
                signed,
                "embargo",
                "issue",
                "101",
                "--effective",
                "2025-01-06",
                "--reason",
                "bridge out",
                "--station",
                "ESC");
        Ran found = deliver(signed, "2025-01-14 09:00");
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("embargo 101");
    }

    @Test
    void aHazardousLadingIsHeldUnlessTheAgreementAllowsIt() {
        Ran found = deliver(signed(), "2025-01-14 09:00", "--commodity", "4930040");
        assertThat(found.exitCode()).isNotZero();
        assertThat(found.output()).contains("hazardous");
    }

    @Test
    void aJunctionWithNoAgreementIsRefused() {
        run(books, RECEIVED);
        Ran found = deliver(books, "2025-01-14 09:00");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void aReadingThatCannotBeReadIsRefused() {
        Ran found = deliver(signed(), "tuesday morning");
        assertThat(found.exitCode()).isNotZero();
    }
}
