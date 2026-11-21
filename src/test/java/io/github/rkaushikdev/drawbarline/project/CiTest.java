package io.github.rkaushikdev.drawbarline.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * What the build has to keep doing.
 *
 * <p>The workflow is not covered by the suite in any other way, and the ways it breaks are all the
 * same shape: a job quietly dropped, a JDK that drifts away from what the toolchain declares, a
 * coverage floor removed because somebody was in a hurry. Reading the workflow and the build and
 * asserting on them is cheap, and it catches exactly those.
 */
class CiTest {

    private static final Path WORKFLOW = Path.of(".github", "workflows", "ci.yml");
    private static final Build BUILD = Build.read();
    private static final String VERIFICATION =
            "^tasks\\.named\\('jacocoTestCoverageVerification', JacocoCoverageVerification\\)";
    private static final String REPORT = "^tasks\\.named\\('jacocoTestReport', JacocoReport\\)";
    private static final Pattern LIMIT = Pattern.compile("\\blimit\\s*\\{([^{}]*)\\}");
    private static final Pattern PINNED = Pattern.compile("[\\w.-]+/[\\w.-]+@v\\d+");

    static Stream<Arguments> jobs() {
        return Stream.of(
                Arguments.of("lint", "./gradlew --no-daemon spotlessCheck"),
                Arguments.of("types", "./gradlew --no-daemon compileJava compileTestJava"),
                Arguments.of("tests", "./gradlew --no-daemon test"),
                Arguments.of("coverage", "./gradlew --no-daemon test jacocoTestCoverageVerification"),
                Arguments.of("migrations", "./gradlew --no-daemon test --tests '*MigrationsTest'"),
                Arguments.of("package", "./gradlew --no-daemon installDist"));
    }

    static String workflow() {
        return Sources.read(WORKFLOW);
    }

    static Map<Object, Object> parsed(String text) {
        return map(new Yaml(new SafeConstructor(new LoaderOptions())).load(text));
    }

    static Map<Object, Object> job(String text, String name) {
        return map(map(parsed(text).get("jobs")).get(name));
    }

    /** The steps of {@code job}, in order. */
    static List<Map<Object, Object>> steps(Map<Object, Object> job) {
        List<Map<Object, Object>> found = new ArrayList<>();
        Object steps = job.get("steps");
        assertThat(steps).isInstanceOf(List.class);
        for (Object step : (List<?>) steps) {
            found.add(map(step));
        }
        return found;
    }

    /** What the steps of {@code job} run, in order. */
    static List<String> runs(Map<Object, Object> job) {
        return steps(job).stream()
                .map(step -> step.get("run"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::strip)
                .toList();
    }

    /** Every action the workflow in {@code text} uses, each as written. */
    static List<String> actions(String text) {
        List<String> found = new ArrayList<>();
        for (Object job : map(parsed(text).get("jobs")).values()) {
            for (Map<Object, Object> step : steps(map(job))) {
                if (step.get("uses") instanceof String uses) {
                    found.add(uses);
                }
            }
        }
        return found;
    }

    /** Whether the workflow in {@code text} states a coverage floor of its own. */
    static boolean carriesAFloor(String text) {
        return Pattern.compile("(?i)\\bminimum\\b|COVEREDRATIO|violationRules")
                .matcher(text)
                .find();
    }

    /** Each coverage limit of the verification task in {@code build}, by counter, with its minimum. */
    static Map<String, Double> limits(Build build) {
        Map<String, Double> found = new LinkedHashMap<>();
        Matcher limit = LIMIT.matcher(build.block(VERIFICATION).orElse(""));
        while (limit.find()) {
            Optional<String> counter = Build.assigned(limit.group(1), "counter");
            Optional<String> minimum = Build.assigned(limit.group(1), "minimum");
            if (counter.isPresent() && minimum.isPresent()) {
                found.put(counter.get(), Double.valueOf(minimum.get()));
            }
        }
        return found;
    }

    private static Map<Object, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return new LinkedHashMap<Object, Object>((Map<?, ?>) value);
    }

    private static Map<Object, Object> java(Map<Object, Object> job) {
        return steps(job).stream()
                .filter(step -> String.valueOf(step.get("uses")).startsWith("actions/setup-java@"))
                .findFirst()
                .map(step -> map(step.get("with")))
                .orElseThrow(() -> new AssertionError("no JDK is set up"));
    }

    @Test
    void thereIsAWorkflow() {
        assertThat(WORKFLOW).isRegularFile();
        assertThat(workflow()).contains("\non: [push, pull_request]\n");
    }

    @ParameterizedTest
    @MethodSource("jobs")
    void everyJobIsStillThere(String name, String command) {
        assertThat(workflow()).contains("\n  " + name + ":\n");
        assertThat(runs(job(workflow(), name))).contains(command);
    }

    @Test
    void theSuiteRunsOnUbuntuWithThePinnedJdk() {
        Map<Object, Object> tests = job(workflow(), "tests");
        assertThat(tests.get("runs-on")).isEqualTo("ubuntu-latest");
        assertThat(java(tests)).containsEntry("distribution", "temurin").containsEntry("java-version", "21");
        assertThat(map(parsed(workflow()).get("jobs")).values())
                .hasSize(6)
                .allSatisfy(one -> assertThat(map(one).get("runs-on")).isEqualTo("ubuntu-latest"));
    }

    @Test
    void theTypesJobCompilesWithEveryWarningAnError() {
        assertThat(runs(job(workflow(), "types"))).contains("./gradlew --no-daemon compileJava compileTestJava");
        assertThat(Build.strings(BUILD.block("^tasks\\.withType\\(JavaCompile\\)\\.configureEach")
                        .orElseThrow()))
                .contains("-Werror")
                .anySatisfy(one -> assertThat(one).startsWith("-Xlint:all"));
    }

    @Test
    void theWorkflowAsksForTheJavaTheToolchainDeclares() {
        Matcher declared = Pattern.compile("JavaLanguageVersion\\.of\\((\\d+)\\)")
                .matcher(BUILD.block("^java", "\\btoolchain").orElseThrow());
        assertThat(declared.find()).isTrue();
        String release = declared.group(1);
        Collection<Object> jobs = map(parsed(workflow()).get("jobs")).values();
        assertThat(jobs).isNotEmpty();
        for (Object one : jobs) {
            assertThat(java(map(one))).containsEntry("java-version", release).containsEntry("cache", "gradle");
        }
    }

    @Test
    void theWorkflowOnlyReads() {
        assertThat(workflow()).contains("\npermissions:\n  contents: read\n");
    }

    @Test
    void aSecondPushCancelsTheFirst() {
        assertThat(map(parsed(workflow()).get("concurrency"))).containsEntry("cancel-in-progress", true);
    }

    @Test
    void theCoverageFloorIsSet() {
        assertThat(limits(BUILD))
                .hasEntrySatisfying("LINE", floor -> assertThat(floor).isGreaterThanOrEqualTo(0.90));
        assertThat(BUILD.block("^tasks\\.named\\('check'\\)").orElseThrow()).contains("jacocoTestCoverageVerification");
    }

    @Test
    void coverageMeasuresBranches() {
        assertThat(limits(Build.of("tasks.named('jacocoTestCoverageVerification', JacocoCoverageVerification) {\n"
                        + "\tviolationRules { rule { limit { counter = 'LINE'\n minimum = 0.90 } } }\n}\n")))
                .containsOnlyKeys("LINE");
        assertThat(limits(BUILD))
                .hasEntrySatisfying("BRANCH", floor -> assertThat(floor).isGreaterThanOrEqualTo(0.80));
    }

    @Test
    void coverageMeasuresTheMainSourcesAndNotTheTests() {
        for (String task : List.of(VERIFICATION, REPORT)) {
            String body = BUILD.block(task).orElseThrow();
            assertThat(body).as(task).contains("sourceSets sourceSets.main").doesNotContain("sourceSets.test");
        }
    }

    @Test
    void theTestForksAreDeclared() {
        String test = BUILD.block("^tasks\\.named\\('test', Test\\)").orElseThrow();
        assertThat(Build.assigned(test, "maxParallelForks"))
                .hasValueSatisfying(forks -> assertThat(Integer.parseInt(forks)).isPositive());
        assertThat(test).contains("finalizedBy tasks.named('jacocoTestReport')");
    }

    @Test
    void theCoverageJobDoesNotCarryItsOwnFloor() {
        assertThat(carriesAFloor("      - run: ./gradlew test -Pcoverage.minimum=0.5\n"))
                .isTrue();
        assertThat(carriesAFloor(workflow())).isFalse();
        assertThat(runs(job(workflow(), "coverage")))
                .contains("./gradlew --no-daemon test jacocoTestCoverageVerification");
    }

    @Test
    void theActionsArePinnedToAMajorVersion() {
        String planted =
                """
                jobs:
                  one:
                    steps:
                      - uses: actions/checkout@main
                      - uses: actions/setup-java@v4
                """;
        assertThat(actions(planted)).containsExactly("actions/checkout@main", "actions/setup-java@v4");
        assertThat(PINNED.matcher("actions/checkout@main").matches()).isFalse();
        List<String> used = actions(workflow());
        assertThat(used).contains("actions/checkout@v4", "actions/setup-java@v4");
        assertThat(used).allSatisfy(one -> assertThat(one).matches(PINNED));
    }

    @Test
    void theDistributionIsInstalledAndRunOnItsOwn() {
        String name = Build.assigned(BUILD.block("^application").orElseThrow(), "applicationName")
                .orElseThrow();
        assertThat(runs(job(workflow(), "package")))
                .containsExactly(
                        "./gradlew --no-daemon installDist", "build/install/" + name + "/bin/" + name + " --version");
    }
}
