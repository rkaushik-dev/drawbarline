package io.github.rkaushikdev.drawbarline.persistence.types;

import io.github.rkaushikdev.drawbarline.money.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Money, stored as a signed count of cents in a 64-bit column, so a month netted the other way round reads back negative.
 *
 * <p>Every read hands back the value object and every write takes one, so no call site has to
 * remember which unit a bare number was in.
 */
@Converter(autoApply = true)
public final class MoneyCents implements AttributeConverter<Money, Long> {

    @Override
    public Long convertToDatabaseColumn(Money value) {
        return value == null ? null : value.cents();
    }

    @Override
    public Money convertToEntityAttribute(Long value) {
        return value == null ? null : new Money(value);
    }
}
