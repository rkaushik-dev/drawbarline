package io.github.rkaushikdev.drawbarline.api;

import static io.github.rkaushikdev.drawbarline.api.Http.body;
import static io.github.rkaushikdev.drawbarline.api.Http.with;
import static io.github.rkaushikdev.drawbarline.api.Http.without;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
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
class ApiEventsTest {

    static final Map<String, Object> CAR = with(ApiFleetTest.CAR, "mark", "CN 400100");

    static final Map<String, Object> RECEIVED = body(
            "car", "CN 400100",
            "kind", "received",
            "station", "ESC",
            "local", "2025-01-06T07:00:00",
            "road", "CN",
            "waybill", 41207);

    static final Map<String, Object> MOVED = body(
            "car", "CN 400100",
            "kind", "moved",
            "station", "PWR",
            "local", "2025-01-06T13:00:00",
            "tenths", 336);

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void fileTheCar() {
        TestDatabase.fresh();
        client = new Http(mvc);
        client.post("/cars", CAR);
    }

    @Test
    void anEventIsRecorded() {
        Http.Answer found = client.post("/events", RECEIVED);
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map().get("kind")).isEqualTo("received");
    }

    @Test
    void aLocalReadingIsReadAtTheStation() {
        Map<String, Object> found = client.post("/events", RECEIVED).map();
        assertThat((String) found.get("moment")).startsWith("2025-01-06T12:00:00");
    }

    @Test
    void theSameReadingAtMarinetteIsAnHourLater() {
        Map<String, Object> found = client.post(
                        "/events", with(RECEIVED, "station", "MRN", "local", "2025-01-06T07:00:00"))
                .map();
        assertThat((String) found.get("moment")).startsWith("2025-01-06T13:00:00");
    }

    @Test
    void anInstantCanBeGivenInstead() {
        Http.Answer found = client.post(
                "/events",
                body(
                        "car", "CN 400100",
                        "kind", "received",
                        "station", "ESC",
                        "moment", "2025-01-06T12:00:00Z",
                        "road", "CN"));
        assertThat(found.status()).isEqualTo(201);
    }

    @Test
    void givingBothClocksIsRefused() {
        Http.Answer found = client.post("/events", with(RECEIVED, "moment", "2025-01-06T12:00:00Z"));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void givingNeitherClockIsRefused() {
        assertThat(client.post("/events", without(RECEIVED, "local")).status()).isEqualTo(422);
    }

    @Test
    void aLocalReadingCarryingAZoneIsRefused() {
        Http.Answer found = client.post("/events", with(RECEIVED, "local", "2025-01-06T07:00:00Z"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aReadingThatNeverHappenedIsRefused() {
        Http.Answer found = client.post("/events", with(RECEIVED, "station", "PWR", "local", "2025-03-09T02:30:00"));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void theAmbiguousHourTakesTheFlag() {
        Http.Answer first = client.post("/events", with(RECEIVED, "station", "PWR", "local", "2025-11-02T01:30:00"));
        assertThat(first.status()).isEqualTo(201);
        Http.Answer second = client.post(
                "/events",
                body(
                        "car", "CN 400100",
                        "kind", "held",
                        "station", "PWR",
                        "local", "2025-11-02T01:30:00",
                        "second_pass", true));
        assertThat(second.status()).isEqualTo(201);
    }

    @Test
    void anInterchangeWithNoRoadIsRefused() {
        assertThat(client.post("/events", without(RECEIVED, "road")).status()).isEqualTo(409);
    }

    @Test
    void aMovementWithNoDistanceIsRefused() {
        Http.Answer found = client.post("/events", without(MOVED, "tenths"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void theSameEventTwiceIsAConflict() {
        client.post("/events", RECEIVED);
        assertThat(client.post("/events", RECEIVED).status()).isEqualTo(409);
    }

    @Test
    void aKindNobodyPublishesIsRefused() {
        assertThat(client.post("/events", with(RECEIVED, "kind", "shunted")).status())
                .isEqualTo(409);
    }

    @Test
    void aStreamComesBackInOrder() {
        client.post("/events", RECEIVED);
        client.post("/events", MOVED);
        assertThat(client.get("/events/cars/CN 400100").each("kind")).containsExactly("received", "moved");
    }

    @Test
    void aCarWithNothingOnItHasAnEmptyStream() {
        assertThat(client.get("/events/cars/CN 400100").rows()).isEmpty();
    }

    @Test
    void theStateReplaysTheStream() {
        client.post("/events", RECEIVED);
        Map<String, Object> found = client.get("/events/cars/CN 400100/state").map();
        assertThat(found.get("on_line")).isEqualTo(true);
        assertThat(found.get("station")).isEqualTo("ESC");
        assertThat(found.get("waybill")).isEqualTo(41207);
        assertThat(found.get("events")).isEqualTo(1);
    }

    @Test
    void aCarOffLineReadsAsSuch() {
        Map<String, Object> found = client.get("/events/cars/CN 400100/state").map();
        assertThat(found.get("on_line")).isEqualTo(false);
        assertThat(found.get("events")).isEqualTo(0);
    }

    @Test
    void aBadOrderedCarIsNotMovable() {
        client.post("/events", RECEIVED);
        client.post(
                "/events",
                body(
                        "car", "CN 400100",
                        "kind", "bad_ordered",
                        "station", "ESC",
                        "local", "2025-01-08T08:00:00"));
        Map<String, Object> found = client.get("/events/cars/CN 400100/state").map();
        assertThat(found.get("bad_ordered")).isEqualTo(true);
        assertThat(found.get("movable")).isEqualTo(false);
    }

    @Test
    void aBatchGoesInTogether() {
        Http.Answer found = client.post("/events/batch", List.of(RECEIVED, MOVED));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map()).isEqualTo(Map.of("recorded", 2));
    }

    @Test
    void aBatchWithABadLineRecordsNoneOfIt() {
        client.post("/events/batch", List.of(RECEIVED, RECEIVED));
        assertThat(client.get("/events/cars/CN 400100").rows()).isEmpty();
    }

    @Test
    void anEventAgainstACarNobodyFiledIsRefused() {
        Http.Answer found = client.post("/events", with(RECEIVED, "car", "WC 000001"));
        assertThat(found.status()).isGreaterThanOrEqualTo(400);
    }
}
