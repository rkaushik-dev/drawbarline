package io.github.rkaushikdev.drawbarline.persistence.types;

import io.github.rkaushikdev.drawbarline.weight.Weight;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A weight, stored as whole pounds in a 64-bit column.
 *
 * <p>Every read hands back the value object and every write takes one, so no call site has to
 * remember which unit a bare number was in.
 */
@Converter(autoApply = true)
public final class WeightPounds implements AttributeConverter<Weight, Long> {

    @Override
    public Long convertToDatabaseColumn(Weight value) {
        return value == null ? null : value.pounds();
    }

    @Override
    public Weight convertToEntityAttribute(Long value) {
        return value == null ? null : new Weight(value);
    }
}
