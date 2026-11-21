package io.github.rkaushikdev.drawbarline.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The build script, read as text with its comments out of the way.
 *
 * <p>The suite runs inside the build, so it cannot ask the build what it configured. What it can do
 * is read the script the same way every time: a block is found by its header and taken between its
 * braces, and the dependencies and plugins are read off their own blocks.
 */
final class Build {

    static final Path SCRIPT = Path.of("build.gradle");
    static final Path SETTINGS = Path.of("settings.gradle");
    static final Path WRAPPER = Path.of("gradle", "wrapper", "gradle-wrapper.properties");

    private static final Pattern DEPENDENCY =
            Pattern.compile("(?m)^\\s*(\\w+)\\s*\\(?\\s*(?:platform\\s*\\(\\s*)?'([^']+)'");
    private static final Pattern PLUGIN =
            Pattern.compile("\\bid\\s*\\(?\\s*'([^']+)'(?:\\s*\\))?(?:[ \\t]+version[ \\t]+'([^']*)')?");
    private static final Pattern ATTRIBUTE = Pattern.compile("'([\\w-]+)'\\s*:\\s*('[^']*'|[\\w.]+)");
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    private final String text;
    private final String code;

    private Build(String text) {
        this.text = text;
        this.code = Sources.code(text);
    }

    /** The project's own build script. */
    static Build read() {
        return of(Sources.read(SCRIPT));
    }

    /** A build script given as text, so a check can be shown what it should find and what it should not. */
    static Build of(String script) {
        return new Build(Sources.withoutComments(script));
    }

    /** The script with its comments blanked. */
    String text() {
        return text;
    }

    /**
     * The inside of the first block whose header matches {@code header}, a regular expression for
     * whatever stands before the opening brace.
     */
    Optional<String> block(String header) {
        return block(text, code, header);
    }

    /** The inside of the first block matching {@code inner} within the block matching {@code outer}. */
    Optional<String> block(String outer, String inner) {
        return block(outer).flatMap(body -> block(body, Sources.code(body), inner));
    }

    /** What a top level property such as {@code version} is set to, quotes removed. */
    Optional<String> property(String name) {
        return assigned(text, name);
    }

    /** Every dependency in the {@code dependencies} block, in the order declared. */
    List<Dependency> dependencies() {
        List<Dependency> found = new ArrayList<>();
        Matcher matched = DEPENDENCY.matcher(block("^dependencies").orElse(""));
        while (matched.find()) {
            found.add(new Dependency(matched.group(1), matched.group(2)));
        }
        return found;
    }

    /** Every plugin in the {@code plugins} block, with the version it names, empty where it names none. */
    Map<String, Optional<String>> plugins() {
        Map<String, Optional<String>> found = new LinkedHashMap<>();
        Matcher matched = PLUGIN.matcher(block("^plugins").orElse(""));
        while (matched.find()) {
            found.put(matched.group(1), Optional.ofNullable(matched.group(2)));
        }
        return found;
    }

    /** The manifest attributes every jar task is given, each with the value as written. */
    Map<String, String> manifest() {
        Map<String, String> found = new LinkedHashMap<>();
        Matcher matched = ATTRIBUTE.matcher(block("^tasks\\.withType\\(Jar\\)\\.configureEach", "\\bmanifest")
                .orElse(""));
        while (matched.find()) {
            found.put(matched.group(1), matched.group(2));
        }
        return found;
    }

    /** What {@code name} is set to inside {@code body}, quotes removed. */
    static Optional<String> assigned(String body, String name) {
        Matcher matched = Pattern.compile("(?m)^\\s*" + Pattern.quote(name) + "\\s*=\\s*(.+?)\\s*$")
                .matcher(body);
        return matched.find() ? Optional.of(unquoted(matched.group(1))) : Optional.empty();
    }

    /** Every single quoted string inside {@code body}, quotes removed. */
    static List<String> strings(String body) {
        List<String> found = new ArrayList<>();
        Matcher matched = QUOTED.matcher(body);
        while (matched.find()) {
            found.add(matched.group(1));
        }
        return found;
    }

    static String unquoted(String value) {
        return value.length() >= 2 && value.startsWith("'") && value.endsWith("'")
                ? value.substring(1, value.length() - 1)
                : value;
    }

    private static Optional<String> block(String text, String code, String header) {
        Matcher matched = Pattern.compile("(?m)" + header + "\\s*\\{").matcher(text);
        if (!matched.find()) {
            return Optional.empty();
        }
        int open = matched.end() - 1;
        int depth = 0;
        for (int at = open; at < code.length(); at++) {
            if (code.charAt(at) == '{') {
                depth++;
            } else if (code.charAt(at) == '}' && --depth == 0) {
                return Optional.of(text.substring(open + 1, at));
            }
        }
        return Optional.empty();
    }

    /**
     * One dependency line.
     *
     * @param configuration where it is declared, such as {@code implementation}
     * @param notation the coordinates as written, {@code group:name} or {@code group:name:version}
     */
    record Dependency(String configuration, String notation) {

        /** The group and the name, without the version. */
        String module() {
            String[] parts = notation.split(":", -1);
            return parts.length < 2 ? notation : parts[0] + ":" + parts[1];
        }

        /** The version as written, empty where the dependency management supplies it. */
        Optional<String> version() {
            String[] parts = notation.split(":", -1);
            return parts.length < 3 ? Optional.empty() : Optional.of(parts[2]);
        }
    }
}
