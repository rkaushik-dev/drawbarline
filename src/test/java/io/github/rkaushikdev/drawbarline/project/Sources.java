package io.github.rkaushikdev.drawbarline.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reading the project's own files: walking a source tree, and seeing a Java source or a build
 * script the way a compiler does, with the comments and the insides of literals out of the way.
 *
 * <p>Blanking keeps every offset and every line break, so a position found in the blanked text is
 * the same position in the file and a line number is the line somebody would open.
 */
final class Sources {

    static final String ROOT_PACKAGE = "io.github.rkaushikdev.drawbarline";
    static final Path MAIN = Path.of("src", "main", "java");
    static final Path TEST = Path.of("src", "test", "java");
    static final Path ROOT = MAIN.resolve(Path.of("io", "github", "rkaushikdev", "drawbarline"));

    /** A parenthesised group with nothing parenthesised inside it, such as an annotation's arguments. */
    private static final Pattern GROUP = Pattern.compile("\\([^()]*\\)");

    /** What may stand between a type's Javadoc and the type: annotations and modifiers. */
    private static final Pattern LEAD_IN = Pattern.compile("(?:\\s|@\\s*[\\w.]+"
            + "|\\b(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\\b)*");

    private Sources() {}

    /** Every Java source under {@code tree}, in path order. */
    static List<Path> javaFiles(Path tree) {
        return files(tree).stream()
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList();
    }

    /** Every regular file under {@code tree}, in path order. */
    static List<Path> files(Path tree) {
        try (Stream<Path> found = Files.walk(tree)) {
            return found.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException trouble) {
            throw new AssertionError("could not walk " + tree, trouble);
        }
    }

    /** {@code tree} and every directory under it, in path order. */
    static List<Path> directories(Path tree) {
        try (Stream<Path> found = Files.walk(tree)) {
            return found.filter(Files::isDirectory).sorted().toList();
        } catch (IOException trouble) {
            throw new AssertionError("could not walk " + tree, trouble);
        }
    }

    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException trouble) {
            throw new AssertionError("could not read " + file, trouble);
        }
    }

    /** The source with its comments and the insides of its literals blanked: what the compiler reads. */
    static String code(String source) {
        return blank(source, true, new ArrayList<>());
    }

    /** The source with its comments blanked and its literals as they are. */
    static String withoutComments(String source) {
        return blank(source, false, new ArrayList<>());
    }

    /** The line, counted from one, that {@code offset} falls on. */
    static int lineOf(String text, int offset) {
        int line = 1;
        for (int at = 0; at < offset; at++) {
            if (text.charAt(at) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Whether the type called {@code type} is declared in {@code source} under Javadoc that says something. */
    static boolean documented(String source, String type) {
        String code = code(source);
        Matcher declared = Pattern.compile("(?:\\b(?:class|interface|enum)|@interface)\\s+" + Pattern.quote(type)
                        + "\\b|\\brecord\\s+" + Pattern.quote(type) + "\\s*[(<]")
                .matcher(code);
        return declared.find() && documentedAt(source, code, declared.start());
    }

    /** Whether the package statement in {@code source} stands under Javadoc that says something. */
    static boolean packageDocumented(String source) {
        String code = code(source);
        Matcher declared = Pattern.compile("\\bpackage\\s+[\\w.]+\\s*;").matcher(code);
        return declared.find() && documentedAt(source, code, declared.start());
    }

    /** The file names that occur more than once among {@code files}, each with how often it does. */
    static Map<String, Long> repeatedNames(List<Path> files) {
        Map<String, Long> counted = files.stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        counted.values().removeIf(count -> count < 2);
        return counted;
    }

    private static boolean documentedAt(String source, String code, int declaration) {
        List<int[]> javadocs = new ArrayList<>();
        blank(source, true, javadocs);
        Optional<int[]> last =
                javadocs.stream().filter(span -> span[1] <= declaration).reduce((first, second) -> second);
        if (last.isEmpty()) {
            return false;
        }
        int[] span = last.get();
        if (source.substring(span[0] + 3, span[1] - 2).replace('*', ' ').isBlank()) {
            return false;
        }
        String between = code.substring(span[1], declaration);
        String flattened;
        do {
            flattened = between;
            between = GROUP.matcher(flattened).replaceAll(" ");
        } while (!between.equals(flattened));
        return LEAD_IN.matcher(between).matches();
    }

    /**
     * Blanks comments, and the insides of literals too when {@code literals} is set, keeping line
     * breaks. Collects where each Javadoc comment starts and ends into {@code javadocs}.
     */
    private static String blank(String source, boolean literals, List<int[]> javadocs) {
        StringBuilder out = new StringBuilder(source);
        int length = source.length();
        int at = 0;
        while (at < length) {
            int end;
            if (source.startsWith("//", at)) {
                end = source.indexOf('\n', at);
                end = end < 0 ? length : end;
                fill(out, at, end);
            } else if (source.startsWith("/*", at)) {
                end = source.indexOf("*/", at + 2);
                end = end < 0 ? length : end + 2;
                if (source.startsWith("/**", at) && end - at > 4) {
                    javadocs.add(new int[] {at, end});
                }
                fill(out, at, end);
            } else if (source.startsWith("\"\"\"", at)) {
                end = closing(source, at + 3, "\"\"\"");
                if (literals) {
                    fill(out, at + 3, Math.max(at + 3, end - 3));
                }
            } else if (source.charAt(at) == '"' || source.charAt(at) == '\'') {
                end = closing(source, at + 1, String.valueOf(source.charAt(at)));
                if (literals) {
                    fill(out, at + 1, Math.max(at + 1, end - 1));
                }
            } else {
                end = at + 1;
            }
            at = end;
        }
        return out.toString();
    }

    /** Where the literal that opened before {@code from} ends, just past {@code quote}. */
    private static int closing(String source, int from, String quote) {
        int at = from;
        while (at < source.length()) {
            if (source.charAt(at) == '\\') {
                at += 2;
            } else if (source.startsWith(quote, at)) {
                return at + quote.length();
            } else if (quote.length() == 1 && source.charAt(at) == '\n') {
                return at;
            } else {
                at++;
            }
        }
        return source.length();
    }

    private static void fill(StringBuilder out, int from, int to) {
        for (int at = from; at < to; at++) {
            if (out.charAt(at) != '\n') {
                out.setCharAt(at, ' ');
            }
        }
    }
}
