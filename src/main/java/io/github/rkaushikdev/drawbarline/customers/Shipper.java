package io.github.rkaushikdev.drawbarline.customers;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;

/**
 * A party we bill demurrage to.
 *
 * <p>A shipper may be on an average agreement, which changes demurrage from a bill per car into a
 * monthly balance of credits and debits. Whether they are is a property of the shipper and not of
 * the car, so it lives here.
 */
public record Shipper(String code, String name, boolean averageAgreement) {

    public Shipper {
        if (!Text.isUpper(code) || !Text.isAlnum(code)) {
            throw new ConfigurationError("a shipper code is upper case", Details.of("code", code));
        }
        if (Text.isBlank(name)) {
            throw new ConfigurationError("a shipper needs a name", Details.of("code", code));
        }
    }

    /** A shipper billed car by car. */
    public Shipper(String code, String name) {
        this(code, name, false);
    }

    /** {@code ESCPAP Escanaba Paper}. */
    @Override
    public String toString() {
        return code + " " + name;
    }
}
