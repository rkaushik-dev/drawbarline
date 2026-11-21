package io.github.rkaushikdev.drawbarline.api;

import static io.github.rkaushikdev.drawbarline.api.Http.body;
import static io.github.rkaushikdev.drawbarline.api.Http.with;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.repairs.Depreciation;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.LaborRate;
import io.github.rkaushikdev.drawbarline.repairs.PriceLine;
import io.github.rkaushikdev.drawbarline.support.Cars;
import java.time.LocalDate;
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
class ApiRepairsTest {

    static final JobCode WHEELSET = new JobCode("4312");
    static final JobCode COUPLER = new JobCode("5104");

    static final Map<String, Object> CARD = body(
            "number", 88104,
            "car", "DBL 104207",
            "performed_by", "CN",
            "performed_on", "2025-01-14",
            "station", "ESC",
            "defects",
                    List.of(
                            body("job", "4312", "why_made", "01", "part_years", 5),
                            body("job", "5104", "why_made", "03")));

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void putThePriceMasterOnFile() {
        TestDatabase.seeded(work -> {
            work.repairPricing().addPrice(new PriceLine(WHEELSET, LocalDate.of(2024, 7, 1), 250, 128000));
            work.repairPricing().addPrice(new PriceLine(COUPLER, LocalDate.of(2024, 7, 1), 125, 84000));
            work.repairPricing().setDepreciation(new Depreciation("43", 4, 20));
            work.repairPricing().fileLabor(new LaborRate(Cars.CN, LocalDate.of(2024, 7, 1), 4200));
        });
        client = new Http(mvc);
        client.post("/cars", ApiFleetTest.CAR);
    }

    private static Map<String, Object> anException(String ground, String filedOn) {
        return body("line", 0, "ground", ground, "disputed", 20000, "filed_on", filedOn);
    }

    @Test
    void aCardIsFiledAndComesBackPriced() {
        Http.Answer found = client.post("/repairs/cards", CARD);
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map().get("total")).isEqualTo(202150);
    }

    @Test
    void aFiledCardSplitsBetweenTheParties() {
        Map<String, Object> found = client.post("/repairs/cards", CARD).map();
        assertThat(found.get("to_owner")).isEqualTo(112900);
        assertThat(found.get("borne_by_the_road")).isEqualTo(89250);
    }

    @Test
    void aLineSaysWhoAnswersForIt() {
        Http.Answer found = client.post("/repairs/cards", CARD);
        assertThat(found.rowsOf("lines").stream().map(one -> one.get("answered_by")))
                .containsExactly("owner", "handling line");
    }

    @Test
    void anOldPartIsDepreciated() {
        Http.Answer found = client.post("/repairs/cards", CARD);
        assertThat(found.rowsOf("lines").get(0).get("material")).isEqualTo(102400);
    }

    @Test
    void theSameCardTwiceIsAConflict() {
        client.post("/repairs/cards", CARD);
        assertThat(client.post("/repairs/cards", CARD).status()).isEqualTo(422);
    }

    @Test
    void aCardForACarNobodyFiledIsANotFound() {
        Http.Answer found = client.post("/repairs/cards", with(CARD, "car", "WC 000001"));
        assertThat(found.status()).isEqualTo(404);
    }

    @Test
    void aJobNobodyPricedCannotBeBilled() {
        Map<String, Object> card = with(CARD, "defects", List.of(body("job", "9999", "why_made", "01")));
        assertThat(client.post("/repairs/cards", card).status()).isEqualTo(422);
    }

    @Test
    void aResponsibilityNobodyPublishesIsRefused() {
        Map<String, Object> card = with(
                CARD, "defects", List.of(body("job", "4312", "why_made", "01", "overridden", "somebody", "note", "x")));
        assertThat(client.post("/repairs/cards", card).status()).isEqualTo(422);
    }

    @Test
    void anOverrideWithAReasonStands() {
        Map<String, Object> card = with(
                CARD,
                "defects",
                List.of(body(
                        "job", "4312",
                        "why_made", "01",
                        "overridden", "handling_line",
                        "note", "flat spot from a hand brake")));
        Http.Answer found = client.post("/repairs/cards", card);
        assertThat(found.rowsOf("lines").get(0).get("answered_by")).isEqualTo("handling line");
    }

    @Test
    void aCardReadsBack() {
        client.post("/repairs/cards", CARD);
        assertThat(client.get("/repairs/cards/88104").map().get("number")).isEqualTo(88104);
    }

    @Test
    void aCardNobodyFiledIsRefused() {
        assertThat(client.get("/repairs/cards/99999").status()).isEqualTo(422);
    }

    @Test
    void cardsComeBackForOneCar() {
        client.post("/repairs/cards", CARD);
        assertThat(client.get("/repairs/cards", "car", "DBL 104207").each("number"))
                .containsExactly(88104);
    }

    @Test
    void cardsComeBackForOneRoad() {
        client.post("/repairs/cards", CARD);
        assertThat(client.get("/repairs/cards", "road", "CN").rows()).hasSize(1);
    }

    @Test
    void askingForNeitherACarNorARoadIsRefused() {
        assertThat(client.get("/repairs/cards").status()).isEqualTo(422);
    }

    @Test
    void anExceptionIsFiled() {
        client.post("/repairs/cards", CARD);
        Http.Answer found = client.post("/repairs/cards/88104/exceptions", anException("depreciation", "2025-02-03"));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.map().get("ground")).isEqualTo("depreciation not allowed for");
    }

    @Test
    void aGroundNobodyPublishesIsRefused() {
        client.post("/repairs/cards", CARD);
        Http.Answer found = client.post("/repairs/cards/88104/exceptions", anException("because", "2025-02-03"));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void anExceptionOutOfTimeIsRefused() {
        client.post("/repairs/cards", CARD);
        Http.Answer found = client.post("/repairs/cards/88104/exceptions", anException("price", "2025-06-03"));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void anOpenExceptionShowsUp() {
        client.post("/repairs/cards", CARD);
        client.post("/repairs/cards/88104/exceptions", anException("depreciation", "2025-02-03"));
        assertThat(client.get("/repairs/exceptions").each("line")).containsExactly(0);
    }

    @Test
    void anAnsweredExceptionLeavesTheOpenList() {
        client.post("/repairs/cards", CARD);
        client.post("/repairs/cards/88104/exceptions", anException("depreciation", "2025-02-03"));
        Http.Answer found = client.post("/repairs/cards/88104/exceptions/0/answer", body("allowed", 8000));
        assertThat(found.map().get("outcome")).isEqualTo("allowed in part");
        assertThat(client.get("/repairs/exceptions").rows()).isEmpty();
    }

    @Test
    void answeringSomethingNeverFiledIsRefused() {
        client.post("/repairs/cards", CARD);
        Http.Answer found = client.post("/repairs/cards/88104/exceptions/0/answer", body("allowed", 1));
        assertThat(found.status()).isEqualTo(422);
    }
}
