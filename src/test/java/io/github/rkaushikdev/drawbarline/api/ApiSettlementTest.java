package io.github.rkaushikdev.drawbarline.api;

import static io.github.rkaushikdev.drawbarline.api.Http.body;
import static io.github.rkaushikdev.drawbarline.api.Http.with;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.carhire.Rate;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.persistence.Numbering;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.support.Cars;
import io.github.rkaushikdev.drawbarline.support.Shippers;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(TestService.class)
class ApiSettlementTest {

    static final Map<String, Object> CLOSE = body("issued", "2025-02-10");

    @Autowired
    private MockMvc mvc;

    private Http client;

    /** One event for the car at Escanaba, read off the clock there on a day in January. */
    static Map<String, Object> anEvent(String kind, int day, int hour, Object... more) {
        Map<String, Object> event = body(
                "car",
                "CN 400100",
                "kind",
                kind,
                "station",
                "ESC",
                "local",
                String.format(Locale.ROOT, "2025-01-%02dT%02d:00:00", day, hour));
        return with(event, more);
    }

    @BeforeEach
    void putTheFilingsOnTheBooks() {
        TestDatabase.seeded(work -> {
            work.numbering().start(Numbering.INVOICES, 5001);
            work.numbering().start(Numbering.STATEMENTS, 9001);
            work.customers().addShipper(Shippers.MILL);
            work.customers().addIndustry(new Industry("ESCPAP1", "mill house", "ESC", Shippers.MILL, 11));
            work.tariffs()
                    .publish(new Tariff(
                            "4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000))));
            work.rates().file(new Band(Cars.HOPPER, 0, 9, LocalDate.of(2024, 7, 1), new Rate(4630, 1210)));
        });
        client = new Http(mvc);
        client.post("/cars", ApiEventsTest.CAR);
    }

    /** A month of traffic: the car taken in, spotted at the mill, released, pulled and handed back. */
    private Http worked() {
        client.post(
                "/events/batch",
                List.of(
                        anEvent("received", 6, 7, "road", "CN", "waybill", 41207),
                        anEvent("placed", 7, 8, "industry", "ESCPAP1", "waybill", 41207),
                        anEvent("released", 14, 15, "industry", "ESCPAP1"),
                        anEvent("pulled", 15, 17, "industry", "ESCPAP1"),
                        anEvent("delivered", 16, 9, "road", "CN")));
        return client;
    }

    @Test
    void nothingIsClosedToBeginWith() {
        assertThat(client.get("/periods").rows()).isEmpty();
    }

    @Test
    void aMonthCloses() {
        Http.Answer found = worked().post("/periods/2025-01/close", CLOSE);
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map().get("month")).isEqualTo("2025-01");
    }

    @Test
    void aCloseReportsTheCarHire() {
        Map<String, Object> found =
                worked().post("/periods/2025-01/close", CLOSE).map();
        assertThat(((Number) found.get("car_hire")).longValue()).isPositive();
        assertThat(found.get("postings")).isEqualTo(1);
    }

    @Test
    void aClosedMonthShowsUp() {
        worked().post("/periods/2025-01/close", CLOSE);
        assertThat(client.get("/periods").value()).isEqualTo(List.of(Map.of("month", "2025-01", "open", false)));
    }

    @Test
    void closingTwiceIsAConflict() {
        worked().post("/periods/2025-01/close", CLOSE);
        Http.Answer found = client.post("/periods/2025-01/close", CLOSE);
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void closingBeforeTheMonthEndsIsRefused() {
        Http.Answer found = worked().post("/periods/2025-01/close", body("issued", "2025-01-20"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aMonthThatCannotBeReadIsRefused() {
        Http.Answer found = worked().post("/periods/january/close", CLOSE);
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aCloseThatFailsLeavesTheMonthOpen() {
        worked().post("/periods/2025-01/close", body("issued", "2025-01-20"));
        assertThat(client.get("/periods").rows()).isEmpty();
    }

    @Test
    void aMonthIsReopenedWithAReason() {
        worked().post("/periods/2025-01/close", CLOSE);
        Http.Answer found =
                client.post("/periods/2025-01/reopen", body("on", "2025-03-01", "reason", "corrected sheet from cn"));
        assertThat(found.status()).isEqualTo(200);
        assertThat(client.get("/periods").rows()).isEmpty();
    }

    @Test
    void reopeningAnOpenMonthIsAConflict() {
        Http.Answer found = worked().post("/periods/2025-01/reopen", body("on", "2025-03-01", "reason", "why not"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aStatementReadsBack() {
        worked().post("/periods/2025-01/close", CLOSE);
        Map<String, Object> found = client.get("/statements/CN/2025-01").map();
        assertThat(found.get("road")).isEqualTo("CN");
        assertThat(found.get("opening")).isEqualTo(0);
        assertThat(((Number) found.get("closing")).longValue()).isPositive();
        assertThat(found.get("direction")).isEqualTo("we pay CN");
    }

    @Test
    void aStatementListsItsKinds() {
        worked().post("/periods/2025-01/close", CLOSE);
        Http.Answer found = client.get("/statements/CN/2025-01");
        List<Object> kinds =
                found.rowsOf("lines").stream().map(one -> one.get("kind")).toList();
        assertThat(kinds).containsExactly("car hire");
    }

    @Test
    void aRoadWithNothingPostedReadsSquare() {
        Map<String, Object> found = worked().get("/statements/ELS/2025-01").map();
        assertThat(found.get("closing")).isEqualTo(0);
        assertThat(found.get("direction")).isEqualTo("square");
    }

    @Test
    void theAccountListsEveryPosting() {
        worked().post("/periods/2025-01/close", CLOSE);
        List<Map<String, Object>> found = client.get("/ledger/CN").rows();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("month")).isEqualTo("2025-01");
        assertThat(found.get(0).get("kind")).isEqualTo("car hire");
    }

    @Test
    void anAccountWithNothingOnItIsEmpty() {
        assertThat(client.get("/ledger/CN").rows()).isEmpty();
    }

    @Test
    void theBooksCountThePostingsAfterAClose() {
        worked().post("/periods/2025-01/close", CLOSE);
        Map<String, Object> found = client.get("/books").map();
        assertThat(found.get("postings")).isEqualTo(1);
        assertThat(found.get("closed_months")).isEqualTo(1);
    }
}
