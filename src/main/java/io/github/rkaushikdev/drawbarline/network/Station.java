package io.github.rkaushikdev.drawbarline.network;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.ZoneId;
import java.util.Set;

/**
 * A place on our line where a car can be set out, with the time zone its clock runs on.
 *
 * <p>The zone is not decoration. Our line crosses the boundary between Eastern and Central at the
 * Delta and Menominee county line, so a car received at Escanaba at 09:00 and delivered at
 * Marinette at 09:00 the same morning was on our rail for an hour.
 */
public record Station(String code, String name, String state, String zone) {

    private static final Set<String> STATES = Set.of("MI", "WI");

    public Station {
        if (!Text.isUpper(code) || !Text.isAlnum(code)) {
            throw new ConfigurationError("a station code is upper case letters and digits", Details.of("code", code));
        }
        if (!STATES.contains(state)) {
            throw new ConfigurationError("this line runs in Michigan and Wisconsin", Details.of("state", state));
        }
        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new ConfigurationError("unknown time zone", Details.of("station", code, "zone", zone));
        }
    }

    /** The zone the station's clock keeps. */
    public ZoneId tz() {
        return ZoneId.of(zone);
    }

    /** {@code Escanaba, MI}. */
    @Override
    public String toString() {
        return name + ", " + state;
    }
}
