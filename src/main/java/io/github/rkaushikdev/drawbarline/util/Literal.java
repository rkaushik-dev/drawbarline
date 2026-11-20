package io.github.rkaushikdev.drawbarline.util;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes a value the way an error's context shows it to an operator.
 *
 * <p>Text is quoted, so {@code station='PWR'} cannot be mistaken for a station called {@code
 * 'PWR'} with the quotes typed in, and so an empty value is visible. Numbers, flags and lists read
 * as themselves: {@code 42}, {@code True}, {@code ['OCO']}. An absent value reads {@code None}.
 */
public final class Literal {

    private Literal() {}

    /** The value as a literal: text quoted and escaped, lists bracketed, absence as {@code None}. */
    public static String of(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof Optional<?> maybe) {
            return maybe.map(Literal::of).orElse("None");
        }
        if (value instanceof CharSequence text) {
            return quoted(text.toString());
        }
        if (value instanceof Character one) {
            return quoted(String.valueOf(one));
        }
        if (value instanceof Boolean flag) {
            return flag ? "True" : "False";
        }
        if (value instanceof Double number) {
            return Numbers.general(number);
        }
        if (value instanceof Float number) {
            return Numbers.general(number.doubleValue());
        }
        if (value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte
                || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> of(entry.getKey()) + ": " + of(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof Set<?> set) {
            if (set.isEmpty()) {
                return "set()";
            }
            return set.stream().map(Literal::of).collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(Literal::of).collect(Collectors.joining(", ", "[", "]"));
        }
        return value.toString();
    }

    /**
     * The value as plain text: text as it is, anything else the way {@link #of} writes it, and a
     * domain value by its own {@code toString}.
     */
    public static String plain(Object value) {
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        return of(value);
    }

    /**
     * Text in single quotes, or double quotes when it holds a single quote and no double one, with
     * the backslash, the quote and anything unprintable escaped.
     */
    public static String quoted(String text) {
        char quote = text.indexOf('\'') >= 0 && text.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder out = new StringBuilder(text.length() + 2).append(quote);
        text.codePoints().forEach(point -> escape(out, point, quote));
        return out.append(quote).toString();
    }

    private static void escape(StringBuilder out, int point, char quote) {
        if (point == quote || point == '\\') {
            out.append('\\').appendCodePoint(point);
        } else if (point == '\t') {
            out.append("\\t");
        } else if (point == '\n') {
            out.append("\\n");
        } else if (point == '\r') {
            out.append("\\r");
        } else if (point < ' ' || point == 0x7f) {
            out.append(String.format(Locale.ROOT, "\\x%02x", point));
        } else if (point < 0x7f || printable(point)) {
            out.appendCodePoint(point);
        } else if (point <= 0xff) {
            out.append(String.format(Locale.ROOT, "\\x%02x", point));
        } else if (point <= 0xffff) {
            out.append(String.format(Locale.ROOT, "\\u%04x", point));
        } else {
            out.append(String.format(Locale.ROOT, "\\U%08x", point));
        }
    }

    private static boolean printable(int point) {
        return switch (Character.getType(point)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SURROGATE,
                    Character.PRIVATE_USE,
                    Character.UNASSIGNED,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.SPACE_SEPARATOR -> false;
            default -> true;
        };
    }
}
