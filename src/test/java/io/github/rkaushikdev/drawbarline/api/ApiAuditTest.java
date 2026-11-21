package io.github.rkaushikdev.drawbarline.api;

import static io.github.rkaushikdev.drawbarline.api.Http.body;
import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.audit.Action;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
class ApiAuditTest {

    static final Instant WHEN = utc(2025, 2, 10, 14, 30);

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void emptyTheBooks() {
        TestDatabase.fresh();
        client = new Http(mvc);
    }

    /** Three decisions on file: a close, a retirement and a reopening. */
    private Http written() {
        TestDatabase.seeded(work -> {
            work.audit().record("rjs", Action.CLOSED_PERIOD, "2025-01", "monthly close", WHEN);
            work.audit().record("mkb", Action.RETIRED_CAR, "DBL 104207", "wreck", WHEN);
            work.audit().record("rjs", Action.REOPENED_PERIOD, "2025-01", "cn sheet", WHEN);
        });
        return client;
    }

    @Test
    void anEmptyTrailReadsBackEmpty() {
        assertThat(client.get("/audit").rows()).isEmpty();
    }

    @Test
    void theTrailReadsBack() {
        List<Map<String, Object>> found = written().get("/audit").rows();
        assertThat(found).hasSize(3);
        assertThat(found.stream().map(one -> one.get("actor")).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("rjs", "mkb");
    }

    @Test
    void anEntryCarriesItsActionAndDetail() {
        Map<String, Object> closed = written().get("/audit").rows().stream()
                .filter(one -> one.get("action").equals("closed_period"))
                .findFirst()
                .orElseThrow();
        assertThat(closed.get("subject")).isEqualTo("2025-01");
        assertThat(closed.get("detail")).isEqualTo("monthly close");
    }

    @Test
    void theTrailFiltersByActor() {
        assertThat(written().get("/audit", "actor", "mkb").each("subject")).containsExactly("DBL 104207");
    }

    @Test
    void theTrailFiltersByAction() {
        assertThat(written().get("/audit", "action", "reopened_period").rows()).hasSize(1);
    }

    @Test
    void anActionNobodyPublishesIsRefused() {
        Http.Answer found = written().get("/audit", "action", "danced");
        assertThat(found.status()).isEqualTo(400);
        assertThat((String) found.map().get("message")).contains("no such action");
    }

    @Test
    void theTrailFiltersBySubject() {
        assertThat(written().get("/audit", "subject", "2025-01").rows()).hasSize(2);
    }

    @Test
    void theLimitHolds() {
        assertThat(written().get("/audit", "limit", 1).rows()).hasSize(1);
    }

    @Test
    void aLimitBelowOneIsRefused() {
        assertThat(written().get("/audit", "limit", 0).status()).isEqualTo(422);
    }

    @Test
    void aLimitPastTheCapIsRefused() {
        assertThat(written().get("/audit", "limit", 5000).status()).isEqualTo(422);
    }

    @Test
    void theActionsArePublished() {
        List<String> found = client.get("/audit/actions").lines();
        assertThat(found)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(Action.values())
                        .map(one -> Text.lower(one.name()))
                        .toList());
    }

    @Test
    void thereIsNoRouteThatWritesAnEntry() {
        assertThat(client.post("/audit", body("actor", "rjs")).status()).isEqualTo(405);
    }

    @Test
    void aPersonWhoDidNothingHasNoTrail() {
        assertThat(written().get("/audit", "actor", "abc").rows()).isEmpty();
    }
}
