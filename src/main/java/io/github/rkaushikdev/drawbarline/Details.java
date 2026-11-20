package io.github.rkaushikdev.drawbarline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the context an error carries: named values, in the order they were given.
 *
 * <p>A value may be absent, because "no road named" is itself worth reporting, so the map
 * tolerates nulls where {@link Map#of} would not.
 */
public final class Details {

    private Details() {}

    public static Map<String, Object> of(String k1, Object v1) {
        return build(new Object[] {k1, v1});
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2) {
        return build(new Object[] {k1, v1, k2, v2});
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return build(new Object[] {k1, v1, k2, v2, k3, v3});
    }

    public static Map<String, Object> of(
            String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        return build(new Object[] {k1, v1, k2, v2, k3, v3, k4, v4});
    }

    public static Map<String, Object> of(
            String k1,
            Object v1,
            String k2,
            Object v2,
            String k3,
            Object v3,
            String k4,
            Object v4,
            String k5,
            Object v5) {
        return build(new Object[] {k1, v1, k2, v2, k3, v3, k4, v4, k5, v5});
    }

    public static Map<String, Object> of(
            String k1,
            Object v1,
            String k2,
            Object v2,
            String k3,
            Object v3,
            String k4,
            Object v4,
            String k5,
            Object v5,
            String k6,
            Object v6) {
        return build(new Object[] {k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6});
    }

    private static Map<String, Object> build(Object[] pairs) {
        Map<String, Object> found = new LinkedHashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            found.put((String) pairs[at], pairs[at + 1]);
        }
        return Collections.unmodifiableMap(found);
    }
}
