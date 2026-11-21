package io.github.rkaushikdev.drawbarline.persistence.types;

import io.github.rkaushikdev.drawbarline.measure.Length;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A length, stored as whole inches.
 *
 * <p>Every read hands back the value object and every write takes one, so no call site has to
 * remember which unit a bare number was in.
 */
@Converter(autoApply = true)
public final class LengthInches implements AttributeConverter<Length, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Length value) {
        return value == null ? null : value.inches();
    }

    @Override
    public Length convertToEntityAttribute(Integer value) {
        return value == null ? null : new Length(value);
    }
}
