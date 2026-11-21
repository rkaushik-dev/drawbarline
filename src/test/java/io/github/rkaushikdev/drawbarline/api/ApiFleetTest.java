package io.github.rkaushikdev.drawbarline.api;

import static io.github.rkaushikdev.drawbarline.api.Http.body;
import static io.github.rkaushikdev.drawbarline.api.Http.with;
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
class ApiFleetTest {

    static final Map<String, Object> CAR = body(
            "mark", "DBL 104207",
            "designation", "LO",
            "type_code", "L351",
            "built", "2020-06-01",
            "light_weight", 63500,
            "gross_rail_load", 286000,
            "outside_length", 714);

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void emptyTheBooks() {
        TestDatabase.fresh();
        client = new Http(mvc);
    }

    private Http stocked() {
        client.post("/cars", CAR);
        client.post("/cars", with(CAR, "mark", "CN 400100"));
        return client;
    }

    private static Map<String, Object> aLease(String lessee, String began, String ended) {
        return body("lessee", lessee, "began", began, "ended", ended);
    }

    @Test
    void aCarIsFiledAndComesBack() {
        Http.Answer found = client.post("/cars", CAR);
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map().get("mark")).isEqualTo("DBL 104207");
    }

    @Test
    void aFiledCarCarriesItsDerivedFields() {
        Map<String, Object> found = client.post("/cars", CAR).map();
        assertThat(found.get("load_limit")).isEqualTo(222500);
        assertThat(found.get("stencil")).isEqualTo("DBL 104207-6");
        assertThat(found.get("kind")).isEqualTo("covered hopper");
        assertThat(found.get("interchange_expires")).isEqualTo("2070-06-01");
    }

    @Test
    void theSameCarTwiceIsAConflict() {
        Http.Answer found = stocked().post("/cars", CAR);
        assertThat(found.status()).isEqualTo(409);
        assertThat(found.map().get("code")).isEqualTo("duplicate_car");
    }

    @Test
    void aCarThatCannotBeReadIsUnprocessable() {
        Http.Answer found = client.post("/cars", with(CAR, "mark", "not a car"));
        assertThat(found.status()).isEqualTo(422);
        assertThat(found.map().get("code")).isEqualTo("mark");
    }

    @Test
    void aCarWhoseCodesDisagreeIsRefused() {
        Http.Answer found = client.post("/cars", with(CAR, "type_code", "X202"));
        assertThat(found.status()).isEqualTo(422);
        assertThat(found.map().get("code")).isEqualTo("car_record");
    }

    @Test
    void aFieldNobodyDeclaredIsRefused() {
        assertThat(client.post("/cars", with(CAR, "colour", "boxcar red")).status())
                .isEqualTo(422);
    }

    @Test
    void aBodyOfTheWrongShapeSaysWhatIsWrongAndWhere() {
        Http.Answer extra = client.post("/cars", with(CAR, "colour", "boxcar red"));
        assertThat(extra.map())
                .isEqualTo(Map.of(
                        "detail",
                        List.of(Map.of(
                                "type",
                                "extra_forbidden",
                                "loc",
                                List.of("body", "colour"),
                                "msg",
                                "Extra inputs are not permitted"))));
        Http.Answer missing = client.post("/cars", Http.without(CAR, "type_code"));
        assertThat(missing.status()).isEqualTo(422);
        assertThat(missing.map())
                .isEqualTo(Map.of(
                        "detail",
                        List.of(Map.of(
                                "type", "missing", "loc", List.of("body", "type_code"), "msg", "Field required"))));
    }

    @Test
    void theRegisterComesBackInMarkOrder() {
        assertThat(stocked().get("/cars").each("mark")).containsExactly("CN 400100", "DBL 104207");
    }

    @Test
    void theRegisterFiltersByOwner() {
        assertThat(stocked().get("/cars", "owner", "DBL").each("mark")).containsExactly("DBL 104207");
    }

    @Test
    void oneCarComesBackOnItsOwn() {
        assertThat(stocked().get("/cars/DBL 104207").map().get("mark")).isEqualTo("DBL 104207");
    }

    @Test
    void aCarReadsBackWithoutTheSpace() {
        assertThat(stocked().get("/cars/DBL104207").map().get("mark")).isEqualTo("DBL 104207");
    }

    @Test
    void aCarNobodyFiledIsANotFound() {
        Http.Answer found = stocked().get("/cars/WC 000001");
        assertThat(found.status()).isEqualTo(404);
        assertThat(found.map().get("code")).isEqualTo("unknown_car");
    }

    @Test
    void aRefusalCarriesItsContext() {
        Map<String, Object> found = stocked().get("/cars/WC 000001").map();
        assertThat(found.get("context")).isEqualTo(Map.of("car", "WC 000001"));
    }

    @Test
    void aCorrectionIsWritten() {
        Http.Answer found = stocked().put("/cars/DBL 104207", with(CAR, "light_weight", 64200));
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.map().get("load_limit")).isEqualTo(221800);
    }

    @Test
    void aCorrectionThatRenamesTheCarIsRefused() {
        Http.Answer found = stocked().put("/cars/DBL 104207", with(CAR, "mark", "DBL 104208"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aNewCarIsOwedToItsOwner() {
        Map<String, Object> found = stocked().get("/cars/CN 400100/ownership").map();
        assertThat(found).isEqualTo(Map.of("owner", "CN", "terms", List.of()));
    }

    @Test
    void aLeaseIsFiled() {
        Http.Answer found = stocked().post("/cars/DBL 104207/leases", aLease("ELS", "2025-01-01", "2025-06-30"));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("terms").get(0).get("lessee").asText()).isEqualTo("ELS");
    }

    @Test
    void aSecondLeaseThatOverlapsIsRefused() {
        stocked().post("/cars/DBL 104207/leases", aLease("ELS", "2025-01-01", "2025-06-30"));
        Http.Answer found = client.post("/cars/DBL 104207/leases", aLease("CN", "2025-06-30", "2025-12-31"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aLeaseSurvivesTheRequest() {
        stocked().post("/cars/DBL 104207/leases", body("lessee", "ELS", "began", "2025-01-01"));
        assertThat(client.get("/cars/DBL 104207/ownership").json().get("terms")).hasSize(1);
    }

    @Test
    void aCarIsRetired() {
        Http.Answer found = stocked().post("/cars/DBL 104207/retirement", body("on", "2025-03-31", "reason", "wreck"));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map()).isEqualTo(Map.of("mark", "DBL 104207", "on", "2025-03-31"));
    }

    @Test
    void retiringTwiceIsAConflict() {
        stocked().post("/cars/DBL 104207/retirement", body("on", "2025-03-31"));
        Http.Answer found = client.post("/cars/DBL 104207/retirement", body("on", "2025-06-30"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aFailedWriteLeavesNothingBehind() {
        client.post("/cars", with(CAR, "light_weight", 300000));
        assertThat(client.get("/cars").rows()).isEmpty();
    }

    @Test
    void theBooksCountWhatWasFiled() {
        assertThat(stocked().get("/books").map().get("cars")).isEqualTo(2);
    }
}
