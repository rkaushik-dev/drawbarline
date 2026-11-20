package io.github.rkaushikdev.drawbarline.config;

import static io.github.rkaushikdev.drawbarline.config.Settings.DEFAULT_DATABASE;
import static io.github.rkaushikdev.drawbarline.config.Settings.PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.logs.Logs;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigTest {

    static int appenders(Logger logger) {
        List<Object> found = new ArrayList<>();
        logger.iteratorForAppenders().forEachRemaining(found::add);
        return found.size();
    }

    @Test
    void theDefaultsStandOnTheirOwn() {
        Settings found = new Settings();
        assertThat(found.database()).isEqualTo(DEFAULT_DATABASE);
        assertThat(found.home()).isEqualTo(new ReportingMark("DBL"));
        assertThat(found.logLevel()).isEqualTo("info");
    }

    @Test
    void settingsPrintWhatMatters() {
        assertThat(new Settings()).hasToString("DBL on drawbarline.db at info");
    }

    @Test
    void aDatabaseOfNothingIsRefused() {
        assertThatThrownBy(() -> new Settings().withDatabase("   ")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aLogLevelNobodyPublishesIsRefused() {
        assertThatThrownBy(() -> new Settings().withLogLevel("verbose"))
                .isInstanceOf(ConfigurationError.class)
                .hasMessageContaining("debug");
    }

    @Test
    void aHomeRoadThatCannotBeAMarkIsRefused() {
        assertThatThrownBy(() -> new Settings().withHomeRoad("the drawbar line"))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aPlainNameBecomesAFileUrl() {
        assertThat(new Settings().url()).isEqualTo("jdbc:h2:file:./drawbarline.db");
    }

    @Test
    void aUrlIsLeftAlone() {
        Settings found = new Settings().withDatabase("jdbc:postgresql://host/books");
        assertThat(found.url()).isEqualTo("jdbc:postgresql://host/books");
    }

    @Test
    void aDatabaseCanBeSwappedWithoutTouchingTheRest() {
        Settings found = new Settings().withLogLevel("debug").withDatabase("other.db");
        assertThat(found.database()).isEqualTo("other.db");
        assertThat(found.logLevel()).isEqualTo("debug");
    }

    @Test
    void anEmptyEnvironmentGivesTheDefaults() {
        assertThat(Settings.fromEnvironment(Map.of())).isEqualTo(new Settings());
    }

    @Test
    void theEnvironmentIsRead() {
        Settings found = Settings.fromEnvironment(Map.of(
                PREFIX + "DATABASE", "books.db",
                PREFIX + "HOME_ROAD", "els",
                PREFIX + "LOG_LEVEL", "DEBUG",
                PREFIX + "ECHO_SQL", "yes"));
        assertThat(found.database()).isEqualTo("books.db");
        assertThat(found.home()).isEqualTo(new ReportingMark("ELS"));
        assertThat(found.logLevel()).isEqualTo("debug");
        assertThat(found.echoSql()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "yes", "true", "ON"})
    void aFlagReadsTheFormsPeopleWrite(String text) {
        assertThat(Settings.fromEnvironment(Map.of(PREFIX + "ECHO_SQL", text)).echoSql())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "no", "false", "OFF", ""})
    void aFlagReadsTheNegativeForms(String text) {
        assertThat(Settings.fromEnvironment(Map.of(PREFIX + "ECHO_SQL", text)).echoSql())
                .isFalse();
    }

    @Test
    void aFlagThatIsNeitherIsRefused() {
        assertThatThrownBy(() -> Settings.fromEnvironment(Map.of(PREFIX + "ECHO_SQL", "sometimes")))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aBadLevelInTheEnvironmentIsRefused() {
        assertThatThrownBy(() -> Settings.fromEnvironment(Map.of(PREFIX + "LOG_LEVEL", "chatty")))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void everySettingIsNamedForTheHelpText() {
        assertThat(Settings.names())
                .containsExactly(PREFIX + "DATABASE", PREFIX + "HOME_ROAD", PREFIX + "LOG_LEVEL", PREFIX + "ECHO_SQL");
    }

    @Test
    void everyNamedSettingActuallyChangesSomething() {
        // A name in the help text that nothing reads is worse than no help text.
        Map<String, String> changed = new LinkedHashMap<>();
        changed.put(PREFIX + "DATABASE", "somewhere.db");
        changed.put(PREFIX + "HOME_ROAD", "ELS");
        changed.put(PREFIX + "LOG_LEVEL", "debug");
        changed.put(PREFIX + "ECHO_SQL", "yes");
        assertThat(changed.keySet()).containsExactlyInAnyOrderElementsOf(Settings.names());
        changed.forEach((name, value) -> assertThat(Settings.fromEnvironment(Map.of(name, value)))
                .as(name)
                .isNotEqualTo(new Settings()));
    }

    @Test
    void aLevelReadsBackAsANumber() {
        assertThat(Logs.levelOf("debug")).isEqualTo(Level.DEBUG);
        assertThat(Logs.levelOf("error").toInt()).isEqualTo(Level.ERROR_INT);
    }

    @Test
    void aLevelNobodyPublishesHasNoNumber() {
        assertThatThrownBy(() -> Logs.levelOf("chatty")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void configuringSetsThePackageLogger() {
        Logger found = Logs.configure(new Settings().withLogLevel("warning"));
        assertThat(found.getName()).isEqualTo(Logs.ROOT);
        assertThat(found.getLevel()).isEqualTo(Level.WARN);
        assertThat(appenders(found)).isEqualTo(1);
    }

    @Test
    void configuringTwiceDoesNotStackHandlers() {
        Logs.configure(new Settings());
        Logger found = Logs.configure(new Settings());
        assertThat(appenders(found)).isEqualTo(1);
    }

    @Test
    void thePackageLoggerDoesNotShoutAtTheRoot() {
        assertThat(Logs.configure(new Settings()).isAdditive()).isFalse();
    }

    @Test
    void aModuleLoggerSitsUnderThePackage() {
        assertThat(Logs.logger("drawbarline.settlement.close").getName()).isEqualTo("drawbarline.settlement.close");
    }

    @Test
    void theRootNameGivesTheRootLogger() {
        assertThat(Logs.logger(Logs.ROOT).getName()).isEqualTo(Logs.ROOT);
    }

    @Test
    void aLoggerOutsideThePackageIsRefused() {
        assertThatThrownBy(() -> Logs.logger("somebody.else")).isInstanceOf(ConfigurationError.class);
    }
}
