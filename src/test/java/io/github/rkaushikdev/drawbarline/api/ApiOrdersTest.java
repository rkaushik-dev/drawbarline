package io.github.rkaushikdev.drawbarline.api;

import static io.github.rkaushikdev.drawbarline.api.Http.body;
import static io.github.rkaushikdev.drawbarline.api.Http.with;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.support.Shippers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(TestService.class)
class ApiOrdersTest {

    static final Map<String, Object> ORDER = body(
            "number", 1,
            "shipper", "ESCPAP",
            "industry", "ESCPAP1",
            "kind", "L",
            "cars", 2,
            "wanted_on", "2025-01-14",
            "placed_on", "2025-01-10");

    static final String MORNING = "2025-01-10T12:00:00Z";

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void fileTheMillAndThreeCars() {
        TestDatabase.seeded(work -> {
            work.customers().addShipper(Shippers.MILL);
            work.customers().addIndustry(new Industry("ESCPAP1", "mill house", "ESC", Shippers.MILL, 11));
        });
        client = new Http(mvc);
        for (int at = 0; at < 3; at++) {
            client.post("/cars", with(ApiFleetTest.CAR, "mark", "CN " + (400100 + at)));
        }
    }

    /** The three cars taken in empty at Escanaba. */
    private Http stocked() {
        List<Map<String, Object>> received = new ArrayList<>();
        for (int at = 0; at < 3; at++) {
            received.add(body(
                    "car", "CN " + (400100 + at),
                    "kind", "received",
                    "station", "ESC",
                    "local", "2025-01-06T07:00:00",
                    "road", "CN"));
        }
        client.post("/events/batch", received);
        return client;
    }

    @Test
    void anOrderIsTaken() {
        Http.Answer found = client.post("/orders", ORDER);
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map().get("kind")).isEqualTo("covered hopper");
    }

    @Test
    void anOrderForAShipperNobodyFiledIsRefused() {
        Http.Answer found = client.post("/orders", with(ORDER, "shipper", "NOBODY"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void anOrderPlacedAfterTheDayItWantsIsRefused() {
        Http.Answer found = client.post("/orders", with(ORDER, "placed_on", "2025-01-20"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aKindNobodyPublishesIsRefused() {
        Http.Answer found = client.post("/orders", with(ORDER, "kind", "Z"));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void theSameNumberTwiceIsRefused() {
        client.post("/orders", ORDER);
        assertThat(client.post("/orders", ORDER).status()).isEqualTo(409);
    }

    @Test
    void theOpenOrdersComeBack() {
        client.post("/orders", ORDER);
        assertThat(client.get("/orders", "on", "2025-01-12").each("number")).containsExactly(1);
    }

    @Test
    void anOrderIsNotOpenAfterItsDay() {
        client.post("/orders", ORDER);
        assertThat(client.get("/orders", "on", "2025-01-15").rows()).isEmpty();
    }

    @Test
    void ordersComeBackForOneShipper() {
        client.post("/orders", ORDER);
        assertThat(client.get("/orders", "on", "2025-01-12", "shipper", "escpap")
                        .rows())
                .hasSize(1);
    }

    @Test
    void anOrderIsCancelled() {
        client.post("/orders", ORDER);
        Http.Answer found = client.post("/orders/1/cancel", body("on", "2025-01-12"));
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.map().get("cancelled_on")).isEqualTo("2025-01-12");
    }

    @Test
    void aCancelledOrderIsNotOpen() {
        client.post("/orders", ORDER);
        client.post("/orders/1/cancel", body("on", "2025-01-12"));
        assertThat(client.get("/orders", "on", "2025-01-13").rows()).isEmpty();
    }

    @Test
    void cancellingTwiceIsRefused() {
        client.post("/orders", ORDER);
        client.post("/orders/1/cancel", body("on", "2025-01-12"));
        assertThat(client.post("/orders/1/cancel", body("on", "2025-01-13")).status())
                .isEqualTo(409);
    }

    @Test
    void theEmptiesComeBack() {
        List<Map<String, Object>> found =
                stocked().get("/empties", "at", MORNING).rows();
        assertThat(found).hasSize(3);
        assertThat(found.get(0).get("station")).isEqualTo("ESC");
    }

    @Test
    void aLoadedCarIsNotAnEmpty() {
        client.post(
                "/events",
                body(
                        "car", "CN 400100",
                        "kind", "received",
                        "station", "ESC",
                        "local", "2025-01-06T07:00:00",
                        "road", "CN",
                        "waybill", 41207));
        assertThat(client.get("/empties", "at", MORNING).rows()).isEmpty();
    }

    @Test
    void theDistributionFillsAnOrder() {
        stocked().post("/orders", ORDER);
        Map<String, Object> found =
                client.get("/distribution", "on", "2025-01-14", "at", MORNING).map();
        assertThat(found.get("filled")).isEqualTo(2);
        assertThat(found.get("unfilled")).isEqualTo(0);
        assertThat(found.get("short")).isEqualTo(Map.of());
    }

    @Test
    void anAssignmentCarriesWhereItComesFrom() {
        stocked().post("/orders", ORDER);
        Http.Answer found = client.get("/distribution", "on", "2025-01-14", "at", MORNING);
        Map<String, Object> first = found.rowsOf("assignments").get(0);
        assertThat(first.get("from_station")).isEqualTo("ESC");
        assertThat(first.get("to_station")).isEqualTo("ESC");
    }

    @Test
    void anOrderBiggerThanTheFleetGoesShort() {
        stocked().post("/orders", with(ORDER, "cars", 5));
        Map<String, Object> found =
                client.get("/distribution", "on", "2025-01-14", "at", MORNING).map();
        assertThat(found.get("unfilled")).isEqualTo(2);
        assertThat(found.get("short")).isEqualTo(Map.of("1", 2));
    }

    @Test
    void aDayWithNoOrdersDistributesNothing() {
        Map<String, Object> found = stocked()
                .get("/distribution", "on", "2025-01-14", "at", MORNING)
                .map();
        assertThat(found.get("filled")).isEqualTo(0);
    }

    @Test
    void theDistributionChangesNothing() {
        stocked().post("/orders", ORDER);
        Object before = client.get("/books").value();
        client.get("/distribution", "on", "2025-01-14");
        assertThat(client.get("/books").value()).isEqualTo(before);
    }
}
