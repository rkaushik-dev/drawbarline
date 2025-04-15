"""Rate bands, in both directions."""

from __future__ import annotations

from drawbarline.carhire.rates import Band, Rate
from drawbarline.persistence.models.rates import RateBandRow


def band_from_row(row: RateBandRow) -> Band:
    return Band(
        type_code=row.type_code,
        from_age=row.from_age,
        to_age=row.to_age,
        effective=row.effective,
        rate=Rate(row.hourly_tenths, row.mileage_mills),
    )


def row_from_band(band: Band) -> RateBandRow:
    return RateBandRow(
        type_code=band.type_code,
        from_age=band.from_age,
        to_age=band.to_age,
        effective=band.effective,
        hourly_tenths=band.rate.hourly_tenths,
        mileage_mills=band.rate.mileage_mills,
    )
