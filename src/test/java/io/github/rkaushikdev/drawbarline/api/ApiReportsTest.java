package io.github.rkaushikdev.drawbarline.api;

import static io.github.rkaushikdev.drawbarline.api.Http.body;
import static io.github.rkaushikdev.drawbarline.api.Http.with;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.carhire.Rate;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.support.Cars;
import io.github.rkaushikdev.drawbarline.support.Shippers;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(TestService.class)
class ApiReportsTest {

    @Autowired
    private MockMvc mvc;

    private Http client;

    /** One event for the car, read off the clock at a station on a day in January. */
    static Map<String, Object> anEvent(String kind, int day, int hour, String station, Object... more) {
        Map<String, Object> event = body(
                "car",
                "CN 400100",
                "kind",
                kind,
                "station",
                station,
                "local",
                String.format(Locale.ROOT, "2025-01-%02dT%02d:00:00", day, hour));
        return with(event, more);
    }

    /**
     * The filings, one car, and a month of its traffic: taken in at Escanaba, run to Powers and
     * back, spotted at the mill, released, pulled, and handed back at Marinette.
     */
    @BeforeEach
    void workAMonth() {
        TestDatabase.seeded(work -> {
            work.customers().addShipper(Shippers.MILL);
            work.customers().addIndustry(new Industry("ESCPAP1", "mill house", "ESC", Shippers.MILL, 11));
            work.tariffs()
                    .publish(new Tariff(
                            "4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000))));
            work.rates().file(new Band(Cars.HOPPER, 0, 9, LocalDate.of(2024, 7, 1), new Rate(4630, 1210)));
        });
        client = new Http(mvc);
        client.post("/cars", ApiEventsTest.CAR);
        client.post(
                "/events/batch",
                List.of(
                        anEvent("received", 6, 7, "ESC", "road", "CN", "waybill", 41207),
                        anEvent("moved", 6, 13, "PWR", "tenths", 336),
                        anEvent("placed", 7, 8, "ESC", "industry", "ESCPAP1", "waybill", 41207),
                        anEvent("released", 14, 15, "ESC", "industry", "ESCPAP1"),
                        anEvent("pulled", 15, 17, "ESC", "industry", "ESCPAP1"),
                        anEvent("delivered", 16, 9, "MRN", "road", "CN")));
    }

    @Test
    void theCarHireReportComesBackAsLines() {
        Http.Answer found = client.get("/reports/carhire/2025-01");
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.lines().get(0)).isEqualTo("CAR HIRE LIABILITY");
    }

    @Test
    void theCarHireDetailCanBeAskedForOneRoad() {
        List<String> found =
                client.get("/reports/carhire/2025-01", "road", "CN").lines();
        assertThat(found.get(0)).isEqualTo("CAR HIRE DETAIL CN");
    }

    @Test
    void theCarHireTotalsComeBackAsData() {
        Map<String, Object> found =
                client.get("/reports/carhire/2025-01/totals").map();
        assertThat(found.keySet()).containsExactly("CN");
        assertThat(((Number) found.get("CN")).longValue()).isPositive();
    }

    @Test
    void thePageAndTheDataAgree() {
        List<String> lines = client.get("/reports/carhire/2025-01").lines();
        Map<String, Object> totals =
                client.get("/reports/carhire/2025-01/totals").map();
        String row =
                lines.stream().filter(line -> line.startsWith("CN")).findFirst().orElseThrow();
        assertThat(row).contains(new Money(((Number) totals.get("CN")).longValue()).toString());
    }

    @Test
    void aMonthThatCannotBeReadIsRefused() {
        assertThat(client.get("/reports/carhire/january").status()).isEqualTo(409);
    }

    @Test
    void theDemurrageReportComesBack() {
        List<String> found = client.get("/reports/demurrage/2025-01").lines();
        assertThat(found.get(0)).isEqualTo("DEMURRAGE DETENTION");
        assertThat(found).anyMatch(line -> line.contains("ESCPAP1"));
    }

    @Test
    void theDemurrageReportSummarisesByShipper() {
        List<String> found =
                client.get("/reports/demurrage/2025-01", "by_shipper", true).lines();
        assertThat(found.get(0)).isEqualTo("DEMURRAGE BY SHIPPER");
    }

    @Test
    void theUtilisationReportComesBack() {
        List<String> found = client.get("/reports/utilisation/2025-01").lines();
        assertThat(found.get(0)).isEqualTo("FLEET UTILISATION");
        assertThat(found).anyMatch(line -> line.contains("CN 400100"));
    }

    @Test
    void theInterchangeReportComesBack() {
        List<String> found = client.get("/reports/interchange/2025-01").lines();
        assertThat(found.get(0)).isEqualTo("INTERCHANGE BALANCE");
    }

    @Test
    void theCrossingsComeBackAsData() {
        List<Map<String, Object>> found =
                client.get("/reports/interchange/2025-01/crossings").rows();
        assertThat(found.stream().map(one -> one.get("junction")).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("ESC", "MRN");
        assertThat(found.stream().mapToInt(one -> (Integer) one.get("net")).sum())
                .isZero();
    }

    @Test
    void theOnHandReportTakesAMoment() {
        List<String> found =
                client.get("/reports/onhand", "at", "2025-01-10T12:00:00Z").lines();
        assertThat(found.get(0)).isEqualTo("CARS ON HAND");
        assertThat(found).anyMatch(line -> line.contains("CN 400100"));
    }

    @Test
    void aCarAlreadyDeliveredIsNotOnHand() {
        List<String> found =
                client.get("/reports/onhand", "at", "2025-01-20T12:00:00Z").lines();
        assertThat(found).anyMatch(line -> line.startsWith("0 cars"));
    }

    @Test
    void theOnHandReportDefaultsToNow() {
        assertThat(client.get("/reports/onhand").status()).isEqualTo(200);
    }

    @Test
    void theAgeReportComesBack() {
        List<String> found = client.get("/reports/age", "on", "2025-07-01").lines();
        assertThat(found.get(0)).isEqualTo("FLEET AGE PROFILE");
    }

    @Test
    void theAgeProfileComesBackAsData() {
        Map<String, Object> found =
                client.get("/reports/age/profile", "on", "2025-07-01").map();
        assertThat(found).isEqualTo(Map.of("0 to 9", 1));
    }

    @Test
    void theAgeReportDefaultsToToday() {
        assertThat(client.get("/reports/age").status()).isEqualTo(200);
    }

    @Test
    void noReportChangesAnything() {
        Object before = client.get("/books").value();
        for (String path : List.of("/reports/carhire/2025-01", "/reports/demurrage/2025-01", "/reports/age")) {
            client.get(path);
        }
        assertThat(client.get("/books").value()).isEqualTo(before);
    }

    @Test
    void everyReportLineFitsThePaper() {
        for (String line : client.get("/reports/carhire/2025-01").lines()) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }
}
