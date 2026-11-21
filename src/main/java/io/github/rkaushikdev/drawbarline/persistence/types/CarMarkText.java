package io.github.rkaushikdev.drawbarline.persistence.types;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A car, stored as the marks and the padded number a clerk would write: {@code DBL 104207}.
 *
 * <p>Every read hands back the value object and every write takes one, so no call site has to
 * remember which unit a bare number was in.
 */
@Converter(autoApply = true)
public final class CarMarkText implements AttributeConverter<CarMark, String> {

    @Override
    public String convertToDatabaseColumn(CarMark value) {
        return value == null ? null : value.toString();
    }

    @Override
    public CarMark convertToEntityAttribute(String value) {
        return value == null ? null : CarMark.parse(value);
    }
}
