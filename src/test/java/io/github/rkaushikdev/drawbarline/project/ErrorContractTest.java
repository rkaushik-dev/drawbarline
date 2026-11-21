package io.github.rkaushikdev.drawbarline.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.Drawbarline;
import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.MarkError;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.api.RequestRejected;
import io.github.rkaushikdev.drawbarline.money.MoneyError;
import io.github.rkaushikdev.drawbarline.persistence.StoreError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Every refusal the application makes looks the same.
 *
 * <p>There is one error taxonomy, and a caller catching its base is supposed to have caught
 * everything the application throws on purpose. That only holds while nobody reaches for one of the
 * JDK's own exceptions or leaves an {@code assert} in, and both are easy to do and invisible until
 * somebody's error handler misses one. An {@code assert} is worse: it does nothing at all unless
 * the JVM was started with assertions on, and a check that only sometimes runs is not one.
 *
 * <p>So this walks the main sources, and then every error class the main classes hold.
 */
class ErrorContractTest {

    private static final List<Path> SHIPPED = Sources.javaFiles(Sources.MAIN);

    /** The JDK's own exceptions, which say nothing about the books when one escapes. */
    private static final Set<String> BUILTINS = Set.of(
            "Exception",
            "RuntimeException",
            "IllegalArgumentException",
            "IllegalStateException",
            "UnsupportedOperationException",
            "NoSuchElementException",
            "NullPointerException",
            "IndexOutOfBoundsException",
            "ArithmeticException",
            "ClassCastException");

    private static final Pattern ASSERT = Pattern.compile("\\bassert\\b");
    private static final Pattern THROWN = Pattern.compile("\\bthrow\\s+new\\s+(?:java\\.(?:lang|util)\\.)?(\\w+)\\s*\\("
            + "|\\b(?:java\\.(?:lang|util)\\.)?(\\w+)\\s*::\\s*new\\b");
    private static final Pattern CODE = Pattern.compile("[a-z]+(?:_[a-z]+)*");

    private static final List<Class<? extends DrawbarlineError>> SCANNED = scanned();
    private static final List<Class<? extends DrawbarlineError>> EVERY =
            SCANNED.stream().filter(ErrorContractTest::shipped).toList();

    /** An error the suite declares for itself: the scan reaches it, and it is no part of the taxonomy. */
    static final class Planted extends DrawbarlineError {

        Planted(String message) {
            super(message);
        }
    }

    static List<Path> shipped() {
        return SHIPPED;
    }

    /** The lines of {@code source} holding an {@code assert} statement. */
    static List<Integer> assertions(String source) {
        String code = Sources.code(source);
        List<Integer> lines = new ArrayList<>();
        Matcher found = ASSERT.matcher(code);
        while (found.find()) {
            lines.add(Sources.lineOf(code, found.start()));
        }
        return lines;
    }

    /** Each JDK exception {@code source} throws or hands to something that throws it, with its line. */
    static List<String> builtinsThrown(String source) {
        String code = Sources.code(source);
        List<String> found = new ArrayList<>();
        Matcher thrown = THROWN.matcher(code);
        while (thrown.find()) {
            String name = thrown.group(1) != null ? thrown.group(1) : thrown.group(2);
            if (BUILTINS.contains(name)) {
                found.add(name + " at line " + Sources.lineOf(code, thrown.start()));
            }
        }
        return found;
    }

    /** Every concrete subclass of the base on the class path under the root package, the suite's included. */
    private static List<Class<? extends DrawbarlineError>> scanned() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(DrawbarlineError.class));
        return scanner.findCandidateComponents(Sources.ROOT_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(ErrorContractTest::load)
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    private static Class<? extends DrawbarlineError> load(String name) {
        try {
            return Class.forName(name, false, ErrorContractTest.class.getClassLoader())
                    .asSubclass(DrawbarlineError.class);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(name, missing);
        }
    }

    /** Whether {@code kind} was compiled from the main sources rather than the suite's. */
    private static boolean shipped(Class<?> kind) {
        return Objects.equals(location(kind), location(Drawbarline.class));
    }

    private static Object location(Class<?> kind) {
        return kind.getProtectionDomain().getCodeSource().getLocation();
    }

    private static String codeOf(Class<?> kind) throws ReflectiveOperationException {
        return (String) kind.getDeclaredField("CODE").get(null);
    }

    private static Path sourceOf(Class<?> kind) {
        Class<?> outermost = kind;
        while (outermost.getEnclosingClass() != null) {
            outermost = outermost.getEnclosingClass();
        }
        return Sources.MAIN.resolve(outermost.getName().replace('.', '/') + ".java");
    }

    private static DrawbarlineError built(Class<? extends DrawbarlineError> kind, String message)
            throws ReflectiveOperationException {
        return kind.getConstructor(String.class).newInstance(message);
    }

    @Test
    void thereAreSourcesToWalk() {
        assertThat(SHIPPED).hasSizeGreaterThan(50);
        // A walk that reads nothing, or a check that sees nothing, passes every source below. So
        // each check is shown here to find what it looks for and to pass over what only looks like it.
        String asserting =
                """
                class Planted {
                    void check(int count) {
                        assert count > 0 : "a count";
                    }
                }
                """;
        String mentioning =
                """
                class Planted {
                    // assert count > 0;
                    /** Callers assert nothing. */
                    String said = "assert count > 0;";
                    boolean asserted = said.isEmpty();
                }
                """;
        assertThat(assertions(asserting)).containsExactly(3);
        assertThat(assertions(mentioning)).isEmpty();
        String throwing =
                """
                class Planted {
                    void refuse(int count) {
                        if (count < 0) {
                            throw new IllegalArgumentException("negative");
                        }
                        throw new java.util.NoSuchElementException();
                    }
                    Object first(java.util.Optional<Object> found) {
                        return found.orElseThrow(IllegalStateException::new);
                    }
                }
                """;
        String refusing =
                """
                class Planted {
                    // throw new IllegalArgumentException("negative");
                    String said = "throw new RuntimeException()";
                    void refuse() {
                        throw new RateError("no rate in effect");
                    }
                }
                """;
        assertThat(builtinsThrown(throwing))
                .containsExactly(
                        "IllegalArgumentException at line 4",
                        "NoSuchElementException at line 6",
                        "IllegalStateException at line 9");
        assertThat(builtinsThrown(refusing)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shipped")
    void noMainSourceAsserts(Path path) {
        assertThat(assertions(Sources.read(path))).as("%s asserts at", path).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shipped")
    void noMainSourceThrowsAJdkException(Path path) {
        assertThat(builtinsThrown(Sources.read(path))).as("%s throws", path).isEmpty();
    }

    @Test
    void everyErrorClassHasItsOwnCode() throws ReflectiveOperationException {
        List<String> codes = new ArrayList<>();
        for (Class<? extends DrawbarlineError> kind : EVERY) {
            String code = codeOf(kind);
            assertThat(built(kind, "something went wrong").code())
                    .as(kind.getName())
                    .isEqualTo(code);
            codes.add(code);
        }
        assertThat(codes).hasSameSizeAs(EVERY).doesNotHaveDuplicates();
    }

    @Test
    void everyCodeIsLowerCaseWithUnderscores() throws ReflectiveOperationException {
        assertThat(CODE.matcher("unknown_car").matches()).isTrue();
        for (String wrong : List.of("Unknown_car", "unknown car", "unknown-car", "unknown_", "")) {
            assertThat(CODE.matcher(wrong).matches()).as(wrong).isFalse();
        }
        for (Class<? extends DrawbarlineError> kind : EVERY) {
            assertThat(codeOf(kind)).as(kind.getName()).matches(CODE);
        }
    }

    @Test
    void everyErrorClassCarriesJavadoc() {
        assertThat(Sources.documented(
                        """
                        /** No rate, or an impossible one. */
                        @Deprecated(since = "0.1")
                        public final class RateError extends DrawbarlineError {}
                        """,
                        "RateError"))
                .isTrue();
        assertThat(Sources.documented(
                        """
                        // No rate, or an impossible one.
                        public final class RateError extends DrawbarlineError {}
                        """,
                        "RateError"))
                .isFalse();
        assertThat(Sources.documented(
                        """
                        /** No rate. */
                        final class Other {}

                        public final class RateError extends DrawbarlineError {}
                        """,
                        "RateError"))
                .isFalse();
        assertThat(Sources.documented("/** */\npublic final class RateError {}\n", "RateError"))
                .isFalse();
        assertThat(EVERY).isNotEmpty().allSatisfy(kind -> assertThat(
                        Sources.documented(Sources.read(sourceOf(kind)), kind.getSimpleName()))
                .as(kind.getName())
                .isTrue());
    }

    @Test
    void everyErrorClassDescendsFromTheBase() {
        assertThat(EVERY).isNotEmpty().allSatisfy(kind -> assertThat(DrawbarlineError.class)
                .isAssignableFrom(kind));
    }

    @Test
    void theTaxonomyIsWorthHaving() {
        assertThat(EVERY).hasSizeGreaterThan(20);
        assertThat(EVERY)
                .contains(
                        DrawbarlineError.class,
                        RateError.class,
                        MarkError.class,
                        UnknownCar.class,
                        MoneyError.class,
                        StoreError.class,
                        RequestRejected.class);
        // The scan reaches the suite's own classes too, and only what the main classes hold counts.
        assertThat(SCANNED).contains(Planted.class);
        assertThat(EVERY).doesNotContain(Planted.class);
    }

    @Test
    void catchingTheBaseCatchesEveryLeaf() throws ReflectiveOperationException {
        assertThat(EVERY).isNotEmpty();
        for (Class<? extends DrawbarlineError> kind : EVERY) {
            DrawbarlineError raised = built(kind, "something went wrong");
            assertThatThrownBy(() -> {
                        throw raised;
                    })
                    .isInstanceOf(DrawbarlineError.class)
                    .isExactlyInstanceOf(kind)
                    .hasMessage("something went wrong");
        }
    }

    @Test
    void everyErrorCarriesItsContextIntoTheMessage() throws ReflectiveOperationException {
        assertThat(EVERY).isNotEmpty();
        for (Class<? extends DrawbarlineError> kind : EVERY) {
            DrawbarlineError raised = kind.getConstructor(String.class, Map.class)
                    .newInstance("something went wrong", Map.of("where", "here"));
            assertThat(raised.getMessage()).as(kind.getName()).contains("where='here'");
        }
    }
}
