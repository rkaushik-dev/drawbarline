package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.drawbarline.api.DrawbarlineApplication;
import io.github.rkaushikdev.drawbarline.config.Settings;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.support.Clerk;
import io.github.rkaushikdev.drawbarline.support.Clerk.Ran;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class CliSettingsTest {

    private static final String PREFIX = Settings.PREFIX;

    @TempDir
    Path tmp;

    private static Ran run(String... args) {
        return Clerk.plain(Map.of(), args);
    }

    private static Ran run(Map<String, String> env, String... args) {
        return Clerk.plain(env, args);
    }

    /** A file database with the schema in it and nothing else. */
    private static String migrated(Path where) {
        String database = where.toString();
        try (Database books = Database.open(new Settings().withDatabase(database))) {
            books.migrate();
        }
        return database;
    }

    private static JsonNode get(int port, String path) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> answer = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return new ObjectMapper().readTree(answer.body());
    }

    @Test
    void theSettingsCommandShowsTheDefaults() {
        Ran found = run("--database", tmp.resolve("books.db").toString(), "settings");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("home road  DBL");
        assertThat(found.output()).contains("log level  info");
    }

    @Test
    void theSettingsCommandListsWhereEachOneComesFrom() {
        Ran found = run("--database", tmp.resolve("books.db").toString(), "settings");
        for (String name : new String[] {PREFIX + "DATABASE", PREFIX + "HOME_ROAD", PREFIX + "LOG_LEVEL"}) {
            assertThat(found.output()).contains(name);
        }
    }

    @Test
    void theUrlIsShownWithItsDriver() {
        Ran found = run("--database", "books.db", "settings");
        assertThat(found.output()).contains("url        jdbc:h2:file:./books.db");
    }

    @Test
    void theEnvironmentSuppliesTheDatabase() {
        String database = tmp.resolve("fromenv.db").toString();
        Ran found = run(Map.of(PREFIX + "DATABASE", database), "settings");
        assertThat(found.output()).contains(database);
    }

    @Test
    void theOptionBeatsTheEnvironment() {
        String chosen = tmp.resolve("chosen.db").toString();
        Ran found = run(Map.of(PREFIX + "DATABASE", "ignored.db"), "--database", chosen, "settings");
        assertThat(found.output()).contains(chosen);
        assertThat(found.output()).doesNotContain("ignored.db");
    }

    @Test
    void theEnvironmentSuppliesTheHomeRoad() {
        Ran found = run(Map.of(PREFIX + "HOME_ROAD", "els"), "settings");
        assertThat(found.output()).contains("home road  els");
    }

    @Test
    void aHomeRoadThatCannotBeAMarkIsRefused() {
        Ran found = run(Map.of(PREFIX + "HOME_ROAD", "the drawbar line"), "settings");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void theLogLevelCanBeSetOnTheCommandLine() {
        Ran found = run("--database", tmp.resolve("b.db").toString(), "--log-level", "debug", "settings");
        assertThat(found.output()).contains("log level  debug");
    }

    @Test
    void aLogLevelNobodyPublishesIsRefused() {
        Ran found = run("--database", tmp.resolve("b.db").toString(), "--log-level", "chatty", "settings");
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void theEnvironmentSuppliesTheLogLevel() {
        Ran found = run(Map.of(PREFIX + "LOG_LEVEL", "warning"), "settings");
        assertThat(found.output()).contains("log level  warning");
    }

    @Test
    void aCommandStillWorksWithNoOptionsAtAll() {
        String database = tmp.resolve("books.db").toString();
        assertThat(run("--database", database, "init").exitCode()).isZero();
        assertThat(run("--database", database, "status").output()).contains("cars      0");
    }

    @Test
    void theApplicationStandsUpFromSettings() throws IOException, InterruptedException {
        String database = migrated(tmp.resolve("api.db"));
        try (ConfigurableApplicationContext app =
                DrawbarlineApplication.run(new Settings().withDatabase(database), "--server.port=0")) {
            int port = ((WebServerApplicationContext) app).getWebServer().getPort();
            assertThat(get(port, "/health").get("status").asText()).isEqualTo("up");
            assertThat(get(port, "/books").get("cars").asInt()).isZero();
        }
    }

    /**
     * The service reads its books out of the environment when nothing else names them: started
     * from the command line with no option, it answers over the books the environment names, and it
     * stops and closes them when it is interrupted.
     */
    @Test
    void theApplicationReadsTheEnvironmentWhenToldNothing() throws Exception {
        String database = migrated(tmp.resolve("env.db"));
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = new Terminal(
                Map.of(PREFIX + "DATABASE", database),
                new BufferedReader(new StringReader("")),
                new PrintWriter(out),
                new PrintWriter(err));
        AtomicInteger code = new AtomicInteger(-1);
        Thread serving = new Thread(() -> code.set(Main.run(terminal, "serve", "--port", "0")));
        serving.start();
        Pattern listening = Pattern.compile("serving \\S+ on http://127\\.0\\.0\\.1:(\\d+)");
        Instant giveUp = Instant.now().plus(Duration.ofMinutes(2));
        Matcher said = listening.matcher(out.toString());
        boolean up = said.find();
        while (!up && serving.isAlive() && Instant.now().isBefore(giveUp)) {
            Thread.sleep(50);
            said = listening.matcher(out.toString());
            up = said.find();
        }
        assertThat(up).as("the service said where it listens: %s %s", out, err).isTrue();
        assertThat(out.toString()).contains(database);
        int port = Integer.parseInt(said.group(1));
        assertThat(get(port, "/health").get("status").asText()).isEqualTo("up");
        assertThat(get(port, "/books").get("cars").asInt()).isZero();
        serving.interrupt();
        serving.join(Duration.ofMinutes(1).toMillis());
        assertThat(serving.isAlive()).isFalse();
        assertThat(code.get()).isEqualTo(Main.OK);
    }

    @Test
    void echoingSqlIsOffUnlessAsked() {
        Ran found = run("--database", tmp.resolve("b.db").toString(), "settings");
        assertThat(found.output()).contains("echo sql   False");
    }

    @Test
    void echoingSqlCanBeTurnedOn() {
        Ran found = run(Map.of(PREFIX + "ECHO_SQL", "yes"), "settings");
        assertThat(found.output()).contains("echo sql   True");
    }

    @Test
    void aFlagThatIsNeitherYesNorNoIsRefused() {
        assertThat(run(Map.of(PREFIX + "ECHO_SQL", "sometimes"), "settings").exitCode())
                .isNotZero();
    }
}
