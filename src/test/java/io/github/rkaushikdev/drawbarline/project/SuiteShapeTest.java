package io.github.rkaushikdev.drawbarline.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.project.Build.Dependency;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What the suite itself has to look like.
 *
 * <p>The test task hands whole classes to its forks, so two tests in different classes may run in
 * different JVMs and two in the same class run in order in one. Each JVM keeps one test database
 * and every test that wants it empties it first. That only holds while no class opens a database
 * of its own as it loads, which is easy to do by adding a static field that looks harmless.
 *
 * <p>These checks are cheap and they catch the shape problems that turn into a test that passes on
 * its own and fails in a run.
 */
class SuiteShapeTest {

    private static final List<Path> TESTS = Sources.javaFiles(Sources.TEST).stream()
            .filter(path -> path.getFileName().toString().endsWith("Test.java"))
            .toList();
    private static final Build BUILD = Build.read();
    private static final String TEST_TASK = "^tasks\\.named\\('test', Test\\)";
    private static final Path PLATFORM_PROPERTIES = Path.of("src", "test", "resources", "junit-platform.properties");

    /** A call that opens a database, the suite's own or a new one. */
    private static final Pattern OPENER = Pattern.compile(
            "\\b(?:TestDatabase\\s*\\.\\s*(?:fresh|seeded)|Database\\s*\\.\\s*(?:open|inMemory|pool|factoryBean)"
                    + "|DriverManager\\s*\\.\\s*getConnection)\\s*\\(");

    private static final Pattern STATIC_FIELD =
            Pattern.compile("\\bstatic\\s+(?:final\\s+)?[\\w<>\\[\\],.? ]+?\\s+\\w+\\s*=([^;]*);");
    private static final Pattern STATIC_BLOCK = Pattern.compile("\\bstatic\\s*\\{");
    private static final Pattern TEST_ANNOTATION =
            Pattern.compile("@\\s*(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b");
    private static final Pattern PARALLEL =
            Pattern.compile("junit\\.jupiter\\.execution\\.parallel\\.enabled\\W+true\\b");

    static List<Path> tests() {
        return TESTS;
    }

    /** Whether a class in {@code source} opens a database while it loads. */
    static boolean opensADatabaseAsItLoads(String source) {
        String code = Sources.code(source);
        Matcher field = STATIC_FIELD.matcher(code);
        while (field.find()) {
            if (OPENER.matcher(field.group(1)).find()) {
                return true;
            }
        }
        Matcher block = STATIC_BLOCK.matcher(code);
        while (block.find()) {
            int depth = 0;
            for (int at = block.end() - 1; at < code.length(); at++) {
                if (code.charAt(at) == '{') {
                    depth++;
                } else if (code.charAt(at) == '}' && --depth == 0) {
                    if (OPENER.matcher(code.substring(block.end(), at)).find()) {
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    /** Whether {@code source} declares a test the JUnit Platform will run. */
    static boolean holdsATest(String source) {
        return TEST_ANNOTATION.matcher(Sources.code(source)).find();
    }

    /** How many forks the test task in {@code build} declares, if it declares any. */
    static Optional<Integer> forks(Build build) {
        return build.block(TEST_TASK)
                .flatMap(body -> Build.assigned(body, "maxParallelForks"))
                .filter(value -> value.matches("\\d+"))
                .map(Integer::valueOf);
    }

    /** Whether {@code text} switches on running the tests inside one class at the same time. */
    static boolean runsInsideAClassAtOnce(String text) {
        return PARALLEL.matcher(Sources.withoutComments(text)).find();
    }

    @Test
    void theTestForksAreDeclaredAndSplitByClass() {
        assertThat(forks(Build.of("tasks.named('test', Test) {\n\tuseJUnitPlatform()\n}\n")))
                .isEmpty();
        assertThat(forks(BUILD)).hasValueSatisfying(count -> assertThat(count).isPositive());
        assertThat(runsInsideAClassAtOnce("junit.jupiter.execution.parallel.enabled = true\n"))
                .isTrue();
        assertThat(runsInsideAClassAtOnce("systemProperty 'junit.jupiter.execution.parallel.enabled', 'true'\n"))
                .isTrue();
        assertThat(runsInsideAClassAtOnce("junit.jupiter.execution.parallel.enabled = false\n"))
                .isFalse();
        assertThat(runsInsideAClassAtOnce(BUILD.text())).isFalse();
        if (Files.exists(PLATFORM_PROPERTIES)) {
            assertThat(runsInsideAClassAtOnce(Sources.read(PLATFORM_PROPERTIES)))
                    .isFalse();
        }
    }

    @Test
    void theTestLauncherIsADeclaredTestDependency() {
        assertThat(BUILD.dependencies())
                .contains(new Dependency("testRuntimeOnly", "org.junit.platform:junit-platform-launcher"));
    }

    @Test
    void thereAreTestClassesToRun() {
        assertThat(TESTS).hasSizeGreaterThan(20);
        // Each check below passes on a file it cannot read, so each is shown here to find what it
        // looks for and to pass over what only looks like it.
        String atLoad =
                """
                class PlantedTest {
                    private static final Database BOOKS = TestDatabase.fresh();
                }
                """;
        String inABlock =
                """
                class PlantedTest {
                    static Database books;

                    static {
                        books = Database.inMemory();
                    }
                }
                """;
        String perTest =
                """
                class PlantedTest {
                    private static Database shared;
                    private static final String URL = "Database.open(";
                    // static final Database BOOKS = TestDatabase.fresh();
                    private Database books = TestDatabase.fresh();

                    static Database reopened() {
                        return Database.open(URL);
                    }
                }
                """;
        assertThat(opensADatabaseAsItLoads(atLoad)).isTrue();
        assertThat(opensADatabaseAsItLoads(inABlock)).isTrue();
        assertThat(opensADatabaseAsItLoads(perTest)).isFalse();
        Path here = Sources.TEST.resolve(
                Path.of("io", "github", "rkaushikdev", "drawbarline", "project", "SuiteShapeTest.java"));
        Path helper =
                Sources.TEST.resolve(Path.of("io", "github", "rkaushikdev", "drawbarline", "support", "Cars.java"));
        assertThat(holdsATest(Sources.read(here))).isTrue();
        assertThat(holdsATest(Sources.read(helper))).isFalse();
    }

    @ParameterizedTest
    @MethodSource("tests")
    void noTestClassOpensADatabaseAsItLoads(Path path) {
        assertThat(opensADatabaseAsItLoads(Sources.read(path)))
                .as(path.toString())
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("tests")
    void everyTestClassHoldsAtLeastOneTest(Path path) {
        assertThat(holdsATest(Sources.read(path)))
                .as("%s declares no tests", path)
                .isTrue();
    }

    @Test
    void noTwoTestClassesShareASimpleName() {
        assertThat(Sources.repeatedNames(List.of(
                        Path.of("a", "PlantedTest.java"),
                        Path.of("b", "PlantedTest.java"),
                        Path.of("b", "OtherTest.java"))))
                .containsOnlyKeys("PlantedTest.java");
        assertThat(TESTS).isNotEmpty();
        assertThat(Sources.repeatedNames(TESTS)).isEmpty();
    }
}
