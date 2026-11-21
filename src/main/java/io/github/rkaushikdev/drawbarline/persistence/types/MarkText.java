package io.github.rkaushikdev.drawbarline.persistence.types;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A reporting mark, stored as its letters.
 *
 * <p>Every read hands back the value object and every write takes one, so no call site has to
 * remember which unit a bare number was in.
 */
@Converter(autoApply = true)
public final class MarkText implements AttributeConverter<ReportingMark, String> {

    @Override
    public String convertToDatabaseColumn(ReportingMark value) {
        return value == null ? null : value.letters();
    }

    @Override
    public ReportingMark convertToEntityAttribute(String value) {
        return value == null ? null : new ReportingMark(value);
    }
}
