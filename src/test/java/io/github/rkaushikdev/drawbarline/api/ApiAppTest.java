package io.github.rkaushikdev.drawbarline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.Drawbarline;
import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.DuplicateCar;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.MarkError;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.persistence.StoreError;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import io.github.rkaushikdev.drawbarline.train.ConsistError;
import java.net.URL;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;

/** The application as a whole, started the way a server starts it, on a port of its own. */
class ApiAppTest {

    private static ConfigurableApplicationContext app;

    private Http client;

    @BeforeAll
    static void startTheApplication() {
        app = DrawbarlineApplication.run(TestDatabase.fresh(), "--server.port=0");
    }

    @AfterAll
    static void stopTheApplication() {
        app.close();
    }

    @BeforeEach
    void emptyTheBooks() {
        TestDatabase.fresh();
        client = new Http(
                MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).build());
    }

    /** An error class nobody gave a status. */
    static final class Unheard extends DrawbarlineError {

        Unheard(String message) {
            super(message);
        }

        @Override
        public String code() {
            return "unheard";
        }
    }

    /** Every concrete error class the application's own code declares, outside the API itself. */
    private static Set<Class<?>> errorClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(DrawbarlineError.class));
        URL shipped =
                DrawbarlineError.class.getProtectionDomain().getCodeSource().getLocation();
        Set<Class<?>> found = new TreeSet<Class<?>>(Comparator.comparing(Class::getName));
        for (BeanDefinition candidate : scanner.findCandidateComponents("io.github.rkaushikdev.drawbarline")) {
            Class<?> type =
                    ClassUtils.resolveClassName(candidate.getBeanClassName(), ApiAppTest.class.getClassLoader());
            boolean inTheApi = type.getPackageName().equals(Errors.class.getPackageName());
            boolean shippedCode =
                    type.getProtectionDomain().getCodeSource().getLocation().equals(shipped);
            if (type != DrawbarlineError.class && !inTheApi && shippedCode) {
                found.add(type);
            }
        }
        return found;
    }

    /** The names of the classes among {@code found} that have no line in the status table. */
    private static Set<String> undecided(Set<Class<?>> found) {
        return found.stream()
                .filter(one -> !Errors.STATUS_FOR.containsKey(one))
                .map(Class::getSimpleName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void healthSaysTheProcessIsUp() {
        Http.Answer found = client.get("/health");
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.map()).isEqualTo(Map.of("status", "up", "version", Drawbarline.VERSION));
    }

    @Test
    void theBooksComeBackEmpty() {
        Http.Answer found = client.get("/books");
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.map()).isEqualTo(Map.of("cars", 0, "events", 0, "postings", 0, "closed_months", 0));
    }

    @Test
    void aRouteNobodyWroteIsANotFound() {
        assertThat(client.get("/nowhere").status()).isEqualTo(404);
    }

    @Test
    void theApplicationNamesItself() {
        JsonNode schema = client.get("/openapi.json").json();
        assertThat(schema.get("info").get("title").asText()).isEqualTo("drawbarline");
        assertThat(schema.get("info").get("version").asText()).isEqualTo(Drawbarline.VERSION);
    }

    @Test
    void aMissingCarIsANotFound() {
        assertThat(Errors.statusFor(new UnknownCar("no record"))).isEqualTo(Errors.NOT_FOUND);
    }

    @Test
    void aDuplicateCarIsAConflict() {
        assertThat(Errors.statusFor(new DuplicateCar("already there"))).isEqualTo(Errors.CONFLICT);
    }

    @Test
    void anEventThatWillNotSitIsAConflict() {
        assertThat(Errors.statusFor(new EventError("out of order"))).isEqualTo(Errors.CONFLICT);
    }

    @Test
    void aClosedMonthIsAConflict() {
        assertThat(Errors.statusFor(new SettlementError("closed"))).isEqualTo(Errors.CONFLICT);
    }

    @Test
    void somethingUnreadableIsUnprocessable() {
        assertThat(Errors.statusFor(new MarkError("cannot read that"))).isEqualTo(422);
    }

    @Test
    void aConfigurationProblemIsABadRequest() {
        assertThat(Errors.statusFor(new ConfigurationError("no database"))).isEqualTo(Errors.BAD_REQUEST);
    }

    @Test
    void anErrorWithNoEntryFallsBackToABadRequest() {
        assertThat(Errors.statusFor(new Unheard("nobody decided"))).isEqualTo(Errors.BAD_REQUEST);
    }

    @Test
    void aRefusalComesBackAsCodeMessageAndContext() {
        Map<String, Object> found =
                Errors.bodyOf(new UnknownCar("no record of that car", Details.of("car", "WC 000001")));
        assertThat(found)
                .isEqualTo(Map.of(
                        "code",
                        "unknown_car",
                        "message",
                        "no record of that car",
                        "context",
                        Map.of("car", "WC 000001")));
    }

    @Test
    void anApplicationWithNoDatabaseRefuses() {
        assertThatThrownBy(() -> new Work(Optional.empty()).database()).isInstanceOf(ConfigurationError.class);
    }

    /** A class nobody decided on falls back to four hundred, which hides it. */
    @Test
    void everyErrorClassTheDomainRaisesHasAStatus() {
        Set<Class<?>> found = errorClasses();
        // The scan reads the classes it should, and the check finds a class with no line.
        assertThat(found).hasSizeGreaterThan(30).contains(UnknownCar.class, ConsistError.class, StoreError.class);
        assertThat(undecided(Set.of(Unheard.class))).containsExactly("Unheard");
        assertThat(undecided(found)).isEmpty();
    }
}
