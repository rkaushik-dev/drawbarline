package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.util.Literal;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Plain data written as JSON for a person to read and a program to diff: two spaces of indent a
 * level, the keys of every object in sorted order, a colon followed by one space, and an empty list
 * or object on one line as {@code []} or {@code {}}.
 *
 * <p>Text is written in ASCII. A quote, a backslash and the usual control characters take their
 * short escapes, and every other character outside printable ASCII is written as {@code \}{@code
 * u} and four lower case hex digits, a character above U+FFFF as its two surrogates. Two exports of
 * the same month are then the same bytes whatever the machine's character set.
 */
final class JsonText {

    private static final String INDENT = "  ";

    private final StringBuilder out = new StringBuilder();

    private JsonText() {}

    /** The value as JSON, without a line feed after it. */
    static String of(Object value) {
        JsonText writer = new JsonText();
        writer.write(value, 0);
        return writer.out.toString();
    }

    private void write(Object value, int depth) {
        switch (value) {
            case null -> out.append("null");
            case Boolean flag -> out.append(flag ? "true" : "false");
            case Integer number -> out.append(number);
            case Long number -> out.append(number);
            case CharSequence text -> text(text.toString());
            case Map<?, ?> map -> object(map, depth);
            case Collection<?> items -> array(items, depth);
            default -> throw new CommandError(Literal.of(value.toString()) + " cannot be written as JSON");
        }
    }

    private void object(Map<?, ?> map, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        Map<String, Object> sorted = new TreeMap<>();
        map.forEach((key, entry) -> sorted.put(String.valueOf(key), entry));
        out.append('{');
        Iterator<Map.Entry<String, Object>> entries = sorted.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Object> entry = entries.next();
            newLine(depth + 1);
            text(entry.getKey());
            out.append(": ");
            write(entry.getValue(), depth + 1);
            if (entries.hasNext()) {
                out.append(',');
            }
        }
        newLine(depth);
        out.append('}');
    }

    private void array(Collection<?> items, int depth) {
        if (items.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        Iterator<?> each = items.iterator();
        while (each.hasNext()) {
            newLine(depth + 1);
            write(each.next(), depth + 1);
            if (each.hasNext()) {
                out.append(',');
            }
        }
        newLine(depth);
        out.append(']');
    }

    private void newLine(int depth) {
        out.append('\n').append(INDENT.repeat(depth));
    }

    private void text(String text) {
        out.append('"');
        for (int at = 0; at < text.length(); at++) {
            char one = text.charAt(at);
            switch (one) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (one >= ' ' && one <= '~') {
                        out.append(one);
                    } else {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) one));
                    }
                }
            }
        }
        out.append('"');
    }
}
