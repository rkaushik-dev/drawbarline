package io.github.rkaushikdev.drawbarline.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.Drawbarline;
import io.github.rkaushikdev.drawbarline.cli.Main;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine.Command;

/**
 * What has to be true of the thing that gets installed.
 *
 * <p>The suite runs against the compiled classes, so nothing in it notices a jar without its
 * manifest or a command that does not resolve to a main class. These read the build script and the
 * source tree instead.
 */
class DistributionPackagingTest {

    private static final Build BUILD = Build.read();
    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    /** What a class must not touch while it loads: the environment, a database or the logging. */
    private static final Pattern OPENS = Pattern.compile(
            "\\b(?:getenv|getProperty|DriverManager|Database|Settings|Logger|LoggerFactory|Logs)\\b|\\bstatic\\s*\\{");

    static List<String> packages() {
        return Sources.directories(Sources.ROOT).stream()
                .map(folder -> Sources.MAIN
                        .relativize(folder)
                        .toString()
                        .replace('/', '.')
                        .replace('\\', '.'))
                .toList();
    }

    /** What {@code source} would touch when its class loads, by name. */
    static List<String> opened(String source) {
        List<String> found = new ArrayList<>();
        Matcher matched = OPENS.matcher(Sources.code(source));
        while (matched.find()) {
            found.add(matched.group().strip());
        }
        return found;
    }

    /** Every directory under {@code tree}, {@code tree} included, without a {@code package-info.java}. */
    static List<Path> withoutPackageInfo(Path tree) {
        return Sources.directories(tree).stream()
                .filter(folder -> !Files.isRegularFile(folder.resolve("package-info.java")))
                .toList();
    }

    private static String manifestValue(Build build, String attribute) {
        return Build.unquoted(build.manifest().getOrDefault(attribute, ""));
    }

    @Test
    void theManifestNamesTheTitleAndTheVersion() {
        assertThat(Build.assigned(Sources.read(Build.SETTINGS), "rootProject.name"))
                .hasValue("drawbarline");
        assertThat(manifestValue(BUILD, "Implementation-Title")).isEqualTo("project.name");
        assertThat(manifestValue(BUILD, "Implementation-Version")).isEqualTo("project.version");
    }

    @Test
    void everyJarCarriesTheManifest() {
        // Configured on one named jar task, the manifest would reach the plain jar and not the Boot
        // jar, so it is configured on every jar task and read only from there.
        Build oneJar = Build.of(
                """
                tasks.named('jar', Jar) {
                \tmanifest {
                \t\tattributes('Implementation-Title': project.name)
                \t}
                }
                """);
        assertThat(oneJar.manifest()).isEmpty();
        assertThat(BUILD.manifest())
                .containsKeys("Implementation-Title", "Implementation-Version", "Implementation-Vendor", "License");
    }

    @Test
    void theCommandIsDeclared() {
        String application = BUILD.block("^application").orElseThrow();
        assertThat(Build.assigned(application, "mainClass")).hasValue(Main.class.getName());
        assertThat(Build.assigned(application, "applicationName")).hasValue("drawbarline");
        assertThat(Main.class.getAnnotation(Command.class).name()).isEqualTo("drawbarline");
    }

    @Test
    void theCommandResolvesToAMainClass() throws ReflectiveOperationException {
        String named = Build.assigned(BUILD.block("^application").orElseThrow(), "mainClass")
                .orElseThrow();
        Class<?> entry = Class.forName(named);
        Method main = entry.getMethod("main", String[].class);
        assertThat(main.getReturnType()).isEqualTo(void.class);
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(Arrays.stream(entry.getMethods()))
                .filteredOn(method -> method.getName().equals("run"))
                .isNotEmpty()
                .allSatisfy(run -> {
                    assertThat(Modifier.isStatic(run.getModifiers())).isTrue();
                    assertThat(run.getReturnType()).isEqualTo(int.class);
                });
    }

    @Test
    void theBootJarRunsTheSameMainClass() {
        assertThat(BUILD.plugins()).containsKey("org.springframework.boot");
        assertThat(Build.assigned(BUILD.block("^springBoot").orElseThrow(), "mainClass"))
                .hasValue("application.mainClass");
    }

    @Test
    void loadingTheApplicationOpensNothing() throws ClassNotFoundException {
        assertThat(opened("class Planted {\n\tstatic final String URL = System.getenv(\"URL\");\n}\n"))
                .containsExactly("getenv");
        assertThat(opened("class Planted {\n\tstatic {\n\t\tLogs.configure(null);\n\t}\n}\n"))
                .containsExactly("static {", "Logs");
        assertThat(opened("/** Opens no Database. */\nclass Planted {\n\tString said = \"Logger\";\n}\n"))
                .isEmpty();
        Path source = Sources.ROOT.resolve("Drawbarline.java");
        assertThat(opened(Sources.read(source))).isEmpty();
        assertThat(Sources.code(Sources.read(source))).doesNotContain("import ");
        assertThat(Class.forName(Drawbarline.class.getName(), true, getClass().getClassLoader()))
                .isEqualTo(Drawbarline.class);
    }

    @Test
    void theVersionIsAReleaseNumber() {
        assertThat(BUILD.property("version")).hasValue(Drawbarline.VERSION);
        assertThat(Drawbarline.VERSION).matches("\\d+\\.\\d+\\.\\d+");
    }

    @Test
    void theBuildDeclaresAReadme() {
        Path readme = Path.of("README.md");
        assertThat(readme).isRegularFile();
        assertThat(Sources.read(readme)).startsWith("# drawbarline\n");
        assertThat(BUILD.property("description"))
                .hasValueSatisfying(one -> assertThat(one).isNotBlank());
    }

    @Test
    void theManifestDeclaresALicence() {
        assertThat(manifestValue(BUILD, "License")).isEqualTo("Proprietary");
    }

    @Test
    void everyPackageHasAPackageInfo(@TempDir Path planted) throws IOException {
        Files.createDirectories(planted.resolve(Path.of("documented", "bare")));
        Files.writeString(planted.resolve("package-info.java"), "/** Planted. */\npackage planted;\n");
        Files.writeString(planted.resolve(Path.of("documented", "package-info.java")), "package planted.documented;\n");
        assertThat(withoutPackageInfo(planted)).containsExactly(planted.resolve(Path.of("documented", "bare")));
        assertThat(Sources.packageDocumented(Sources.read(planted.resolve("package-info.java"))))
                .isTrue();
        assertThat(Sources.packageDocumented(Sources.read(planted.resolve(Path.of("documented", "package-info.java")))))
                .isFalse();
        assertThat(Sources.packageDocumented("/** The first. */\nimport java.util.List;\npackage planted;\n"))
                .isFalse();
        assertThat(packages()).hasSizeGreaterThan(40).contains(Sources.ROOT_PACKAGE, Sources.ROOT_PACKAGE + ".api");
        assertThat(withoutPackageInfo(Sources.ROOT)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("packages")
    void everyPackageSaysWhatItIs(String name) {
        Path info = Sources.MAIN.resolve(name.replace('.', '/')).resolve("package-info.java");
        String source = Sources.read(info);
        assertThat(Sources.packageDocumented(source)).as(name).isTrue();
        assertThat(source.stripLeading()).as(name).startsWith("/**");
    }

    @Test
    void everyMainSourceOpensWithJavadoc() {
        assertThat(Sources.documented(
                        "import java.util.List;\n\n/** What it is. */\npublic final class Planted {}\n", "Planted"))
                .isTrue();
        assertThat(Sources.documented(
                        "/** What it is. */\npackage planted;\n\nimport java.util.List;\n\n"
                                + "public final class Planted {}\n",
                        "Planted"))
                .isFalse();
        assertThat(Sources.documented(
                        "public final class Planted {\n\t/** What it does. */\n\tvoid work() {}\n}\n", "Planted"))
                .isFalse();
        List<String> missing = new ArrayList<>();
        List<Path> sources = Sources.javaFiles(Sources.MAIN).stream()
                .filter(path -> !path.endsWith("package-info.java"))
                .toList();
        for (Path path : sources) {
            String type = path.getFileName().toString().replace(".java", "");
            if (!Sources.documented(Sources.read(path), type)) {
                missing.add(path.toString());
            }
        }
        assertThat(sources).hasSizeGreaterThan(50);
        assertThat(missing).isEmpty();
    }

    @Test
    void theMigrationsAreResourcesOutsideTheSources() {
        assertThat(MIGRATIONS.resolve("V1__baseline.sql")).isRegularFile();
        assertThat(Sources.files(MIGRATIONS))
                .hasSizeGreaterThan(1)
                .allSatisfy(path -> assertThat(path.getFileName().toString()).endsWith(".sql"));
        List<Path> underTheSources = Sources.files(Sources.MAIN);
        assertThat(underTheSources).isNotEmpty();
        assertThat(underTheSources)
                .noneSatisfy(path -> assertThat(path.toString()).endsWith(".sql"));
    }
}
