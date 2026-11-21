package io.github.rkaushikdev.drawbarline.project;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.Drawbarline;
import io.github.rkaushikdev.drawbarline.project.Build.Dependency;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What the build declares has to match what the sources import.
 *
 * <p>The suite is the one place every test library is always on the class path, so the suite on
 * its own cannot notice that a main source imports something the build does not ship. It runs
 * green here and the installed command fails to start on somebody else's machine. So this reads the
 * build script and the sources instead of running the code.
 */
class PackagingTest {

    private static final List<Path> SHIPPED = Sources.javaFiles(Sources.MAIN);
    private static final Build BUILD = Build.read();

    /**
     * Which declared dependency each imported package comes from. The longest prefix that fits an
     * import names its dependency; an import no prefix fits comes from nothing the build declares.
     */
    private static final Map<String, String> PROVIDED_BY = Map.ofEntries(
            entry("ch.qos.logback.", "org.springframework.boot:spring-boot-starter-logging"),
            entry("com.fasterxml.jackson.", "org.springframework.boot:spring-boot-starter-web"),
            entry("com.zaxxer.hikari.", "org.springframework.boot:spring-boot-starter-data-jpa"),
            entry("io.swagger.v3.", "org.springdoc:springdoc-openapi-starter-webmvc-ui"),
            entry("jakarta.persistence.", "org.springframework.boot:spring-boot-starter-data-jpa"),
            entry("jakarta.validation.", "org.springframework.boot:spring-boot-starter-validation"),
            entry("org.flywaydb.", "org.flywaydb:flyway-core"),
            entry("org.h2.", "com.h2database:h2"),
            entry("org.hibernate.", "org.springframework.boot:spring-boot-starter-data-jpa"),
            entry("org.slf4j.", "org.springframework.boot:spring-boot-starter-logging"),
            entry("org.springframework.", "org.springframework.boot:spring-boot-starter-web"),
            entry("org.springframework.orm.", "org.springframework.boot:spring-boot-starter-data-jpa"),
            entry("picocli.", "info.picocli:picocli"));

    /** Libraries only the suite has: a main source importing one fails to start outside it. */
    private static final List<String> TEST_ONLY = List.of(
            "org.junit.",
            "org.assertj.",
            "org.mockito.",
            "org.springframework.test.",
            "org.springframework.boot.test.");

    /** Every package the JDK has, which needs no declaration. */
    private static final Set<String> JDK = ModuleFinder.ofSystem().findAll().stream()
            .map(ModuleReference::descriptor)
            .map(ModuleDescriptor::packages)
            .flatMap(Set::stream)
            .collect(Collectors.toUnmodifiableSet());

    private static final Pattern IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");
    private static final Pattern EXACT = Pattern.compile("\\d+(?:\\.\\d+)+");

    static List<Path> shipped() {
        return SHIPPED;
    }

    /** Everything {@code source} imports, static imports included. */
    static List<String> imports(String source) {
        List<String> found = new ArrayList<>();
        Matcher matched = IMPORT.matcher(Sources.code(source));
        while (matched.find()) {
            found.add(matched.group(1));
        }
        return found;
    }

    /** What {@code source} imports from outside the JDK and the application that {@code build} does not declare. */
    static List<String> undeclared(String source, Build build) {
        Set<String> declared = build.dependencies().stream()
                .filter(one -> one.configuration().equals("implementation"))
                .map(Dependency::module)
                .collect(Collectors.toSet());
        List<String> found = new ArrayList<>();
        for (String imported : imports(source)) {
            if (imported.startsWith(Sources.ROOT_PACKAGE + ".") || fromTheJdk(imported)) {
                continue;
            }
            Optional<String> provider = PROVIDED_BY.keySet().stream()
                    .filter(imported::startsWith)
                    .max(Comparator.comparingInt(String::length))
                    .map(PROVIDED_BY::get);
            if (provider.isEmpty()) {
                found.add(imported + " comes from no dependency the build knows");
            } else if (!declared.contains(provider.get())) {
                found.add(imported + " needs " + provider.get() + ", which the build does not declare");
            }
        }
        return found;
    }

    /** What {@code source} imports from a library only the suite has. */
    static List<String> testOnly(String source) {
        return imports(source).stream()
                .filter(imported -> TEST_ONLY.stream().anyMatch(imported::startsWith))
                .toList();
    }

    /** Whether a version is one exact release, or left to the dependency management to state. */
    static boolean pinned(Dependency dependency) {
        return dependency
                .version()
                .map(version -> EXACT.matcher(version).matches())
                .orElse(true);
    }

    /** Whether {@code build} points the source sets somewhere other than the standard layout. */
    static boolean movesTheSources(Build build) {
        return Pattern.compile("\\bsourceSets\\s*\\{|\\bsrcDirs?\\b")
                .matcher(build.text())
                .find();
    }

    /** The class file format version stated in the first eight bytes of a class file. */
    static int majorVersion(byte[] header) {
        assertThat(header).hasSizeGreaterThanOrEqualTo(8).startsWith(0xCA, 0xFE, 0xBA, 0xBE);
        return ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
    }

    private static boolean fromTheJdk(String imported) {
        String candidate = imported;
        for (int dot = candidate.lastIndexOf('.'); dot > 0; dot = candidate.lastIndexOf('.')) {
            candidate = candidate.substring(0, dot);
            if (JDK.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] headerOf(Path classFile) {
        try (InputStream in = Files.newInputStream(classFile)) {
            return in.readNBytes(8);
        } catch (IOException trouble) {
            throw new UncheckedIOException(trouble);
        }
    }

    private static Path mainClasses() {
        try {
            return Path.of(Drawbarline.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException trouble) {
            throw new AssertionError(trouble);
        }
    }

    @Test
    void theApplicationShipsAtLeastOneSource() {
        assertThat(SHIPPED).isNotEmpty();
        // Every check below passes on a source that imports nothing and on a build it cannot read,
        // so the two import checks are first shown to find what they look for and to pass over what
        // they should not.
        assertThat(BUILD.dependencies()).isNotEmpty();
        assertThat(undeclared(
                        """
                        import java.util.List;
                        import javax.sql.DataSource;
                        import static java.util.Map.entry;
                        import io.github.rkaushikdev.drawbarline.money.Money;
                        import picocli.CommandLine.Command;
                        import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
                        """,
                        BUILD))
                .isEmpty();
        assertThat(undeclared("import com.google.common.collect.ImmutableList;\n", BUILD))
                .containsExactly("com.google.common.collect.ImmutableList comes from no dependency the build knows");
        String script = Sources.read(Build.SCRIPT);
        String withoutPicocli = script.replace("implementation 'info.picocli:picocli:4.7.7'", "");
        assertThat(withoutPicocli).isNotEqualTo(script);
        assertThat(undeclared("import picocli.CommandLine;\n", Build.of(withoutPicocli)))
                .containsExactly("picocli.CommandLine needs info.picocli:picocli, which the build does not declare");
        String mixed =
                """
                import org.assertj.core.api.Assertions;
                import static org.junit.jupiter.api.Assertions.fail;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.testing.Probe;
                import org.springframework.web.bind.annotation.GetMapping;
                """;
        assertThat(testOnly(mixed))
                .containsExactly(
                        "org.assertj.core.api.Assertions",
                        "org.junit.jupiter.api.Assertions.fail",
                        "org.springframework.boot.test.context.SpringBootTest");
    }

    @ParameterizedTest
    @MethodSource("shipped")
    void everyThirdPartyImportIsADeclaredRuntimeDependency(Path path) {
        assertThat(undeclared(Sources.read(path), BUILD)).as(path.toString()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("shipped")
    void noMainSourceImportsATestOnlyLibrary(Path path) {
        assertThat(testOnly(Sources.read(path))).as(path.toString()).isEmpty();
    }

    @Test
    void everyRuntimeDependencyIsPinned() {
        assertThat(pinned(new Dependency("implementation", "info.picocli:picocli:4.7.7")))
                .isTrue();
        assertThat(pinned(new Dependency("implementation", "org.flywaydb:flyway-core")))
                .isTrue();
        for (String moving : List.of("4.+", "latest.release", "[4.7,5)", "4.7.7-SNAPSHOT", "")) {
            assertThat(pinned(new Dependency("implementation", "info.picocli:picocli:" + moving)))
                    .as(moving)
                    .isFalse();
        }
        // A dependency without a version takes the one Spring Boot's dependency management states.
        assertThat(BUILD.plugins()).containsKeys("org.springframework.boot", "io.spring.dependency-management");
        List<Dependency> runtime = BUILD.dependencies().stream()
                .filter(one -> !one.configuration().startsWith("test"))
                .toList();
        assertThat(runtime)
                .extracting(Dependency::module)
                .contains("info.picocli:picocli", "org.springframework.boot:spring-boot-starter-web");
        assertThat(runtime)
                .allSatisfy(one -> assertThat(pinned(one)).as(one.notation()).isTrue());
    }

    @Test
    void everyDevelopmentDependencyIsPinned() {
        List<Dependency> development = BUILD.dependencies().stream()
                .filter(one -> one.configuration().startsWith("test"))
                .toList();
        assertThat(development)
                .extracting(Dependency::module)
                .contains("org.springframework.boot:spring-boot-starter-test");
        assertThat(development)
                .allSatisfy(one -> assertThat(pinned(one)).as(one.notation()).isTrue());
        // The plugins that are not part of Gradle itself each name one release, and Gradle itself
        // is the one the wrapper names.
        assertThat(Build.of("plugins {\n\tid 'java'\n\tid 'com.diffplug.spotless' version '7.+'\n}\n")
                        .plugins())
                .containsEntry("java", Optional.empty())
                .containsEntry("com.diffplug.spotless", Optional.of("7.+"));
        Map<String, Optional<String>> plugins = BUILD.plugins();
        assertThat(plugins).containsKeys("java", "application", "jacoco", "com.diffplug.spotless");
        plugins.forEach((id, version) -> {
            if (id.contains(".")) {
                assertThat(version).as(id).hasValueSatisfying(one -> assertThat(one)
                        .matches(EXACT));
            }
        });
        assertThat(Build.assigned(BUILD.block("^jacoco").orElseThrow(), "toolVersion"))
                .hasValueSatisfying(one -> assertThat(one).matches(EXACT));
        assertThat(Build.assigned(Sources.read(Build.WRAPPER), "distributionUrl"))
                .hasValueSatisfying(url -> assertThat(url).endsWith("/gradle-8.14.8-bin.zip"));
    }

    @Test
    void theSourcesLiveUnderTheRootPackage() {
        assertThat(movesTheSources(Build.of("sourceSets {\n\tmain {\n\t\tjava { srcDirs = ['lib'] }\n\t}\n}\n")))
                .isTrue();
        assertThat(movesTheSources(Build.of("// sourceSets { main { java { srcDirs = ['lib'] } } }\n")))
                .isFalse();
        assertThat(movesTheSources(BUILD)).isFalse();
        assertThat(SHIPPED).isNotEmpty().allSatisfy(path -> {
            assertThat(path.startsWith(Sources.ROOT)).as(path.toString()).isTrue();
            Matcher declared = PACKAGE.matcher(Sources.code(Sources.read(path)));
            assertThat(declared.find()).as(path.toString()).isTrue();
            String folder = Sources.MAIN
                    .relativize(path.getParent())
                    .toString()
                    .replace('/', '.')
                    .replace('\\', '.');
            assertThat(declared.group(1)).as(path.toString()).isEqualTo(folder);
        });
    }

    @Test
    void theDeclaredJavaMatchesWhatTheCompilerTargets() {
        assertThat(BUILD.block("^java", "\\btoolchain").orElseThrow())
                .contains("languageVersion = JavaLanguageVersion.of(21)");
        assertThat(Runtime.version().feature()).isEqualTo(21);
        assertThat(majorVersion(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 61}))
                .isEqualTo(61);
        Path compiled = mainClasses();
        List<Path> classes = Sources.files(compiled).stream()
                .filter(path -> path.getFileName().toString().endsWith(".class"))
                .toList();
        assertThat(SHIPPED.stream().filter(path -> !path.endsWith("package-info.java")))
                .isNotEmpty()
                .allSatisfy(path -> {
                    String name = path.getFileName().toString().replace(".java", ".class");
                    Path relative = Sources.MAIN.relativize(path.resolveSibling(name));
                    assertThat(classes).contains(compiled.resolve(relative.toString()));
                });
        assertThat(classes).allSatisfy(path -> assertThat(majorVersion(headerOf(path)))
                .as(path.toString())
                .isEqualTo(65));
    }

    @Test
    void theCompilerTreatsEveryWarningAsAnError() {
        String commentedOut =
                """
                tasks.withType(JavaCompile).configureEach {
                \t// options.compilerArgs << '-Werror'
                \toptions.compilerArgs << '-Xlint:all'
                }
                """;
        assertThat(compilerArguments(Build.of(commentedOut))).containsExactly("-Xlint:all");
        List<String> arguments = compilerArguments(BUILD);
        assertThat(arguments).anySatisfy(one -> assertThat(one).startsWith("-Xlint:all"));
        assertThat(arguments).contains("-Werror");
    }

    @Test
    void theSuiteRunsFromTheTestSourcesOnTheJUnitPlatform() {
        assertThat(Sources.TEST.resolve(
                        Path.of("io", "github", "rkaushikdev", "drawbarline", "project", "PackagingTest.java")))
                .isRegularFile();
        assertThat(Sources.javaFiles(Sources.TEST)).hasSizeGreaterThan(20);
        assertThat(Build.of("tasks.named('test', Test) {\n\t// useJUnitPlatform()\n}\n")
                        .block("^tasks\\.named\\('test', Test\\)")
                        .orElseThrow())
                .doesNotContain("useJUnitPlatform()");
        assertThat(BUILD.block("^tasks\\.named\\('test', Test\\)").orElseThrow())
                .contains("useJUnitPlatform()");
    }

    private static List<String> compilerArguments(Build build) {
        return Build.strings(build.block("^tasks\\.withType\\(JavaCompile\\)\\.configureEach")
                .orElse(""));
    }
}
