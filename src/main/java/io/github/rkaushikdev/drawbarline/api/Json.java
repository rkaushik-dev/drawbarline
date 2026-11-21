package io.github.rkaushikdev.drawbarline.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * How the service reads and writes JSON, in one place so the application and its tests cannot
 * drift apart.
 *
 * <p>Field names are snake case. A field nobody declared is refused rather than shrugged off,
 * because a client that misspells {@code light_weight} would otherwise file a car with no weight
 * on it and never hear about it. Dates and moments are ISO strings. A number is not text and text
 * is not a number: a client that sends {@code "mark": 42} has sent something wrong, and a whole
 * number arriving as {@code 63500.5} is refused rather than cut short.
 */
public final class Json {

    private Json() {}

    /** Apply the service's JSON rules to a builder: the application's own, or a test's. */
    public static void configure(Jackson2ObjectMapperBuilder builder) {
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .featuresToEnable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .featuresToDisable(
                        DeserializationFeature.ACCEPT_FLOAT_AS_INT, SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .postConfigurer(Json::strictText);
    }

    /** A mapper built the way the service builds its own. */
    public static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        configure(builder);
        return builder.build();
    }

    private static void strictText(ObjectMapper mapper) {
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }
}
