"""The car hire rate repository.

The table is small and it is read on every car of every month, so the whole
thing is loaded into the domain table rather than queried band by band. That
is not an optimisation somebody guessed at: a rate lookup happens once a piece
of a span, and a span with a birthday and a rate change in it is four
lookups, and there are hundreds of cars.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.carhire.rates import Band, RateTable
from drawbarline.errors import RateError
from drawbarline.fleet.aar import CarTypeCode
from drawbarline.persistence.mappers.rates import band_from_row, row_from_band
from drawbarline.persistence.models.rates import RateBandRow


class RateRepository:
    """Every filed car hire band."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def file(self, band: Band) -> Band:
        standing = self.session.execute(
            select(RateBandRow).where(
                RateBandRow.type_code == band.type_code,
                RateBandRow.from_age == band.from_age,
                RateBandRow.effective == band.effective,
            )
        ).first()
        if standing is not None:
            raise RateError(
                "that band is already filed",
                type_code=str(band.type_code),
                effective=str(band.effective),
            )
        self.session.add(row_from_band(band))
        self.session.flush()
        return band

    def file_all(self, bands: list[Band]) -> int:
        for band in bands:
            self.file(band)
        return len(bands)

    def bands_for(self, type_code: CarTypeCode) -> list[Band]:
        rows = self.session.execute(
            select(RateBandRow)
            .where(RateBandRow.type_code == type_code)
            .order_by(RateBandRow.effective, RateBandRow.from_age)
        ).scalars()
        return [band_from_row(row) for row in rows]

    def effective_dates(self, type_code: CarTypeCode) -> list[date]:
        rows = self.session.execute(
            select(RateBandRow.effective)
            .where(RateBandRow.type_code == type_code)
            .distinct()
            .order_by(RateBandRow.effective)
        ).scalars()
        return list(rows)

    def table(self) -> RateTable:
        """Load the whole schedule, which is what a close works against."""
        table = RateTable()
        rows = self.session.execute(
            select(RateBandRow).order_by(RateBandRow.effective, RateBandRow.from_age)
        ).scalars()
        for row in rows:
            table.add(band_from_row(row))
        return table

    def __len__(self) -> int:
        return len(list(self.session.execute(select(RateBandRow.id)).scalars()))
