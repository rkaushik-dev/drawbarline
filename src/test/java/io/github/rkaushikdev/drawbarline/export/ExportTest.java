package io.github.rkaushikdev.drawbarline.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.config.Settings;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.service.Service;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.support.Cars;
import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import io.github.rkaushikdev.drawbarline.support.Office;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportTest {

    private static final Period JANUARY = new Period(2025, 1);

    @TempDir
    Path tmp;

    /**
     * The loaded railroad with a day of interchange on it, received at Escanaba at seven in the
     * morning and handed back at Marinette ten days later, and January closed by rjs.
     */
    private static Database closed() {
        return closeJanuary(Office.loaded());
    }

    /** The same railroad and the same January, in a file of its own that the command line opens. */
    private String closedBooks() {
        String database = Clerk.books(tmp);
        try (Database books = Database.open(new Settings().withDatabase(database))) {
            closeJanuary(books);
        }
        return database;
    }

    private static Database closeJanuary(Database books) {
        Network line = Reference.drawbarLine();
        CarMark car = CarMark.parse("CN 400100");
        try (UnitOfWork work = books.unitOfWork()) {
            work.events()
                    .record(Event.builder(
                                    EventKind.RECEIVED,
                                    car,
                                    Clock.toUtc(line.station("ESC"), LocalDateTime.of(2025, 1, 6, 7, 0)),
                                    "ESC")
                            .road(Cars.CN)
                            .waybill(41207)
                            .build());
            work.events()
                    .record(Event.builder(
                                    EventKind.DELIVERED,
                                    car,
                                    Clock.toUtc(line.station("MRN"), LocalDateTime.of(2025, 1, 16, 9, 0)),
                                    "MRN")
                            .road(Cars.CN)
                            .build());
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            Service.runClose(work, JANUARY, LocalDate.of(2025, 2, 10), "rjs");
            work.commit();
        }
        return books;
    }

    private static Map<String, Object> bundleOf(Database database) {
        try (UnitOfWork work = database.unitOfWork()) {
            return Export.month(work, JANUARY);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> bundle, String part) {
        return (List<Map<String, Object>>) bundle.get(part);
    }

    @Test
    void aBundleSaysWhatItIs() {
        Map<String, Object> found = bundleOf(closed());
        assertThat(found.get("shape")).isEqualTo(Export.SHAPE);
        assertThat(found.get("version")).isEqualTo(Export.VERSION);
    }

    @Test
    void aBundleNamesItsMonth() {
        assertThat(bundleOf(closed()).get("period")).isEqualTo("2025-01");
    }

    @Test
    void aClosedMonthSaysSo() {
        assertThat(bundleOf(closed()).get("closed")).isEqualTo(true);
    }

    @Test
    void anOpenMonthSaysSo() {
        assertThat(bundleOf(Office.loaded()).get("closed")).isEqualTo(false);
    }

    @Test
    void aBundleCarriesThePostings() {
        List<Map<String, Object>> found = rows(bundleOf(closed()), "postings");
        assertThat(found.stream().map(one -> one.get("road"))).containsExactly("CN");
        assertThat(found.get(0).get("kind")).isEqualTo("car_hire");
        assertThat((Long) found.get(0).get("cents")).isPositive();
    }

    @Test
    void aBundleCarriesTheStatements() {
        List<Map<String, Object>> found = rows(bundleOf(closed()), "statements");
        assertThat(found.get(0).get("road")).isEqualTo("CN");
        assertThat((Long) found.get(0).get("closing_cents")).isPositive();
        assertThat(found.get(0).get("direction")).isEqualTo("we pay CN");
    }

    @Test
    void aStatementCarriesItsLines() {
        Map<String, Object> found = rows(bundleOf(closed()), "statements").get(0);
        assertThat(rows(found, "lines").stream().map(one -> one.get("kind"))).containsExactly("car_hire");
    }

    @Test
    void aBundleCarriesTheEvents() {
        List<Map<String, Object>> found = rows(bundleOf(closed()), "events");
        assertThat(found).hasSize(2);
        assertThat(found.stream().map(one -> one.get("kind")).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("received", "delivered");
    }

    @Test
    void anEventCarriesItsMomentInUtc() {
        Map<String, Object> found = rows(bundleOf(closed()), "events").get(0);
        assertThat((String) found.get("moment")).endsWith("+00:00");
    }

    @Test
    void aBundleCarriesTheAuditTrail() {
        List<Map<String, Object>> found = rows(bundleOf(closed()), "audit");
        assertThat(found.get(0).get("actor")).isEqualTo("rjs");
        assertThat(found.get(0).get("action")).isEqualTo("closed_period");
    }

    @Test
    void aBundleSaysWhetherTheMonthBalances() {
        assertThat(bundleOf(closed()).get("balances")).isEqualTo(true);
    }

    @Test
    void aBundleIsJson() throws JsonProcessingException {
        ObjectMapper json = new ObjectMapper();
        String text = json.writeValueAsString(bundleOf(closed()));
        assertThat(json.readTree(text).get("period").asText()).isEqualTo("2025-01");
    }

    @Test
    void aMonthWithNothingInItStillExports() {
        Map<String, Object> found = bundleOf(Office.loaded());
        assertThat(rows(found, "postings")).isEmpty();
        assertThat(rows(found, "events")).isEmpty();
    }

    @Test
    void theCountsSayHowMuchIsInIt() {
        Map<String, Integer> found = Export.counts(bundleOf(closed()));
        assertThat(found.get("events")).isEqualTo(2);
        assertThat(found.get("postings")).isEqualTo(1);
    }

    @Test
    void theExportCommandWritesToStandardOutput() throws JsonProcessingException {
        Ran found = Clerk.run(closedBooks(), "export", "2025-01");
        assertThat(found.exitCode()).isZero();
        assertThat(new ObjectMapper().readTree(found.output()).get("period").asText())
                .isEqualTo("2025-01");
    }

    @Test
    void theExportCommandWritesAFile() throws IOException {
        Path out = tmp.resolve("january.json");
        Ran found = Clerk.run(closedBooks(), "export", "2025-01", "--out", out.toString());
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("2 events");
        assertThat(new ObjectMapper()
                        .readTree(Files.readString(out, StandardCharsets.UTF_8))
                        .get("period")
                        .asText())
                .isEqualTo("2025-01");
    }

    @Test
    void aMonthThatCannotBeReadIsRefused() {
        assertThat(Clerk.run(closedBooks(), "export", "january").exitCode()).isNotZero();
    }

    @Test
    void exportingChangesNothing() {
        String closed = closedBooks();
        String before = Clerk.run(closed, "status").output();
        Clerk.run(closed, "export", "2025-01");
        assertThat(Clerk.run(closed, "status").output()).isEqualTo(before);
    }
}
