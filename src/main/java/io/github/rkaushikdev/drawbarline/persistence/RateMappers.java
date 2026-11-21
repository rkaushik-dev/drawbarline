package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.carhire.Rate;
import io.github.rkaushikdev.drawbarline.persistence.models.RateBandRow;

/**
 * Car hire rate bands, in both directions.
 *
 * <p>The rates are whole numbers in their own units, tenths of a cent an hour and mills a mile, so
 * there is no floating point anywhere between the filing and the statement.
 */
public final class RateMappers {

    private RateMappers() {}

    public static Band bandFromRow(RateBandRow row) {
        return new Band(
                row.getTypeCode(),
                row.getFromAge(),
                row.getToAge(),
                row.getEffective(),
                new Rate(row.getHourlyTenths(), row.getMileageMills()));
    }

    public static RateBandRow rowFromBand(Band band) {
        return new RateBandRow(
                band.typeCode(),
                band.fromAge(),
                band.toAge(),
                band.effective(),
                Stored.whole(band.rate().hourlyTenths(), "hourly_tenths"),
                Stored.whole(band.rate().mileageMills(), "mileage_mills"));
    }
}
