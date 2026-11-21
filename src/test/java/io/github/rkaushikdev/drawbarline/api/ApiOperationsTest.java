package io.github.rkaushikdev.drawbarline.api;

import static io.github.rkaushikdev.drawbarline.api.Http.body;
import static io.github.rkaushikdev.drawbarline.api.Http.with;
import static io.github.rkaushikdev.drawbarline.api.Http.without;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.support.Shippers;
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
class ApiOperationsTest {

    static final Map<String, Object> WAYBILL = body(
            "number", 41207,
            "dated", "2025-01-14",
            "car", "CN 400100",
            "shipper", "PWRGRN",
            "consignee", "ESCPAP",
            "route", List.of("PWR", "HRM", "BKR", "ESC"),
            "lading", body("stcc", "0113710", "name", "wheat", "pounds", 198000, "bulk", true));

    static final Map<String, Object> EMBARGO = body(
            "number", 101,
            "effective", "2025-01-06",
            "reason", "bridge out at hermansville",
            "station", "HRM");

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void fileTheMillAndThreeCars() {
        TestDatabase.seeded(work -> {
            work.customers().addShipper(Shippers.MILL);
            work.customers().addIndustry(new Industry("ESCPAP1", "mill house", "ESC", Shippers.MILL, 2));
        });
        client = new Http(mvc);
        for (String mark : List.of("CN 400100", "CN 400101", "CN 400102")) {
            client.post("/cars", with(ApiFleetTest.CAR, "mark", mark));
        }
    }

    @Test
    void aWaybillIsIssuedAndComesBack() {
        Http.Answer found = client.post("/waybills", WAYBILL);
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map().get("origin")).isEqualTo("PWR");
        assertThat(found.map().get("destination")).isEqualTo("ESC");
    }

    @Test
    void aWaybillCarriesItsMileage() {
        assertThat(client.post("/waybills", WAYBILL).map().get("tenths")).isEqualTo(336);
    }

    @Test
    void aLoadedWaybillNamesItsCommodity() {
        Map<String, Object> found = client.post("/waybills", WAYBILL).map();
        assertThat(found.get("loaded")).isEqualTo(true);
        assertThat(found.get("commodity")).isEqualTo("wheat");
    }

    @Test
    void anEmptyWaybillCarriesNoCommodity() {
        Map<String, Object> found =
                client.post("/waybills", with(WAYBILL, "lading", null)).map();
        assertThat(found.get("loaded")).isEqualTo(false);
        assertThat(found).containsEntry("commodity", null);
    }

    @Test
    void aHazardousLadingIsMarked() {
        Map<String, Object> bill =
                with(WAYBILL, "lading", body("stcc", "4930040", "name", "caustic soda", "pounds", 180000));
        assertThat(client.post("/waybills", bill).map().get("hazardous")).isEqualTo(true);
    }

    @Test
    void theSameNumberTwiceIsRefused() {
        client.post("/waybills", WAYBILL);
        assertThat(client.post("/waybills", WAYBILL).status()).isEqualTo(422);
    }

    @Test
    void aRouteThroughAPlaceWeDoNotServeIsRefused() {
        Map<String, Object> bill = with(WAYBILL, "route", List.of("PWR", "CHI"));
        assertThat(client.post("/waybills", bill).status()).isEqualTo(422);
    }

    @Test
    void aWaybillForACarNobodyFiledIsANotFound() {
        assertThat(client.post("/waybills", with(WAYBILL, "car", "WC 000001")).status())
                .isEqualTo(404);
    }

    @Test
    void aWaybillReadsBackByNumber() {
        client.post("/waybills", WAYBILL);
        assertThat(client.get("/waybills/41207").map().get("number")).isEqualTo(41207);
    }

    @Test
    void waybillsComeBackForOneCar() {
        client.post("/waybills", WAYBILL);
        assertThat(client.get("/waybills", "car", "CN 400100").each("number")).containsExactly(41207);
    }

    @Test
    void waybillsComeBackOverASpan() {
        client.post("/waybills", WAYBILL);
        assertThat(client.get("/waybills", "first", "2025-01-01", "last", "2025-01-31")
                        .rows())
                .hasSize(1);
    }

    @Test
    void anEmbargoIsIssued() {
        Http.Answer found = client.post("/embargoes", EMBARGO);
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map().get("station")).isEqualTo("HRM");
    }

    @Test
    void anEmbargoThatRestrictsNothingIsRefused() {
        assertThat(client.post("/embargoes", without(EMBARGO, "station")).status())
                .isEqualTo(409);
    }

    @Test
    void theLiveEmbargoesComeBack() {
        client.post("/embargoes", EMBARGO);
        assertThat(client.get("/embargoes", "on", "2025-01-10").each("number")).containsExactly(101);
    }

    @Test
    void anEmbargoIsNotLiveBeforeItStarts() {
        client.post("/embargoes", EMBARGO);
        assertThat(client.get("/embargoes", "on", "2025-01-05").rows()).isEmpty();
    }

    @Test
    void anEmbargoIsLifted() {
        client.post("/embargoes", EMBARGO);
        Http.Answer found = client.post("/embargoes/101/lift", body("on", "2025-01-20"));
        assertThat(found.map().get("expires")).isEqualTo("2025-01-20");
        assertThat(client.get("/embargoes", "on", "2025-01-21").rows()).isEmpty();
    }

    @Test
    void liftingBeforeItTookEffectIsRefused() {
        client.post("/embargoes", EMBARGO);
        assertThat(client.post("/embargoes/101/lift", body("on", "2025-01-01")).status())
                .isEqualTo(409);
    }

    @Test
    void anEmptySwitchListComesBack() {
        Http.Answer found = client.get("/switchlist/ESC", "day", "2025-01-14", "at", "2025-01-14T12:00:00Z");
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.rowsOf("moves")).isEmpty();
    }

    @Test
    void anOrderPlacesACar() {
        client.post(
                "/events",
                body(
                        "car", "CN 400100",
                        "kind", "received",
                        "station", "ESC",
                        "local", "2025-01-06T07:00:00",
                        "road", "CN"));
        Http.Answer found = client.get(
                "/switchlist/ESC", "day", "2025-01-14", "at", "2025-01-14T12:00:00Z", "order", "CN 400100=ESCPAP1");
        assertThat(found.rowsOf("moves").stream().map(one -> one.get("kind"))).containsExactly("place");
        assertThat(found.map().get("worked")).isEqualTo(1);
    }

    @Test
    void anOrderThatCannotBeReadIsRefused() {
        Http.Answer found = client.get("/switchlist/ESC", "day", "2025-01-14", "order", "nonsense");
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void anOrderForACarNotOnOurRailIsRefused() {
        Http.Answer found = client.get("/switchlist/ESC", "day", "2025-01-14", "order", "CN 400100=ESCPAP1");
        assertThat(found.status()).isEqualTo(409);
    }
}
