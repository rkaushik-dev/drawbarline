package io.github.rkaushikdev.drawbarline.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A small client over MockMvc for the API tests: send a request, get the status and the parsed
 * body back. A path is sent as written, so a car's marks can carry their space.
 */
public final class Http {

    static final ObjectMapper JSON = Json.mapper();

    private final MockMvc mvc;

    public Http(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** A GET, with query parameters given as name, value, name, value. A name may repeat. */
    public Answer get(String path, Object... query) {
        return send(withQuery(MockMvcRequestBuilders.get(uri(path)), query));
    }

    public Answer post(String path, Object body) {
        return send(json(MockMvcRequestBuilders.post(uri(path)), body));
    }

    public Answer put(String path, Object body) {
        return send(json(MockMvcRequestBuilders.put(uri(path)), body));
    }

    /** Send a request built by hand. */
    public Answer send(MockHttpServletRequestBuilder request) {
        try {
            return new Answer(mvc.perform(request).andReturn());
        } catch (Exception failed) {
            throw new AssertionError("the request did not complete", failed);
        }
    }

    /** A JSON object to send, built from name, value, name, value. */
    public static Map<String, Object> body(Object... pairs) {
        Map<String, Object> built = new LinkedHashMap<>();
        for (int at = 0; at + 1 < pairs.length; at += 2) {
            built.put((String) pairs[at], pairs[at + 1]);
        }
        return built;
    }

    /** A copy of {@code body} with some fields replaced or added, given as name, value, ... */
    public static Map<String, Object> with(Map<String, Object> body, Object... pairs) {
        Map<String, Object> changed = new LinkedHashMap<>(body);
        changed.putAll(body(pairs));
        return changed;
    }

    /** A copy of {@code body} without one field. */
    public static Map<String, Object> without(Map<String, Object> body, String name) {
        Map<String, Object> changed = new LinkedHashMap<>(body);
        changed.remove(name);
        return changed;
    }

    static String write(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    /** A path as it goes on the wire, a space in it written {@code %20}. */
    static URI uri(String path) {
        return URI.create(path.replace(" ", "%20"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(write(body));
    }

    private static MockHttpServletRequestBuilder withQuery(MockHttpServletRequestBuilder request, Object... query) {
        for (int at = 0; at + 1 < query.length; at += 2) {
            request.queryParam((String) query[at], String.valueOf(query[at + 1]));
        }
        return request;
    }

    /** What came back. */
    public record Answer(MvcResult result) {

        public int status() {
            return result.getResponse().getStatus();
        }

        public String text() {
            return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        }

        public JsonNode json() {
            try {
                return JSON.readTree(text());
            } catch (JsonProcessingException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }

        /** The body as plain maps, lists and scalars, to compare whole. */
        public Object value() {
            return JSON.convertValue(json(), Object.class);
        }

        /** A JSON object body as a map. */
        public Map<String, Object> map() {
            return JSON.convertValue(json(), new TypeReference<Map<String, Object>>() {});
        }

        /** A JSON array body as a list of maps. */
        public List<Map<String, Object>> rows() {
            return JSON.convertValue(json(), new TypeReference<List<Map<String, Object>>>() {});
        }

        /** An array field of a JSON object body as a list of maps. */
        public List<Map<String, Object>> rowsOf(String field) {
            return JSON.convertValue(json().get(field), new TypeReference<List<Map<String, Object>>>() {});
        }

        /** A JSON array body as a list of strings. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            json().forEach(line -> found.add(line.asText()));
            return found;
        }

        /** One field of each row of a JSON array body. */
        public List<Object> each(String field) {
            return rows().stream().map(row -> row.get(field)).toList();
        }
    }
}
