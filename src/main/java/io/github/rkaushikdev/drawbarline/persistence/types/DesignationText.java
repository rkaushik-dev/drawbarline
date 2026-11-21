package io.github.rkaushikdev.drawbarline.persistence.types;

import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A mechanical designation, stored as its letters.
 *
 * <p>Every read hands back the value object and every write takes one, so no call site has to
 * remember which unit a bare number was in.
 */
@Converter(autoApply = true)
public final class DesignationText implements AttributeConverter<MechanicalDesignation, String> {

    @Override
    public String convertToDatabaseColumn(MechanicalDesignation value) {
        return value == null ? null : value.letters();
    }

    @Override
    public MechanicalDesignation convertToEntityAttribute(String value) {
        return value == null ? null : new MechanicalDesignation(value);
    }
}
