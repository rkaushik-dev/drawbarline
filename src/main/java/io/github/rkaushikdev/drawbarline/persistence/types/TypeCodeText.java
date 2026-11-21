package io.github.rkaushikdev.drawbarline.persistence.types;

import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * An AAR car type code, stored as its four characters.
 *
 * <p>Every read hands back the value object and every write takes one, so no call site has to
 * remember which unit a bare number was in.
 */
@Converter(autoApply = true)
public final class TypeCodeText implements AttributeConverter<CarTypeCode, String> {

    @Override
    public String convertToDatabaseColumn(CarTypeCode value) {
        return value == null ? null : value.code();
    }

    @Override
    public CarTypeCode convertToEntityAttribute(String value) {
        return value == null ? null : new CarTypeCode(value);
    }
}
