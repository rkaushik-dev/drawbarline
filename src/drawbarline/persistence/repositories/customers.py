"""The customer repository.

The switching days go in and out through one pair of functions so the comma
separated column is only understood in one place. Every day is checked against
the week on the way back, because a hand edited row with a typo in it would
otherwise turn into an industry that is never switched and cars that never
come off it.
"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.customers import (
    WEEKDAYS,
    CustomerError,
    CustomerRegister,
    Industry,
    Shipper,
)
from drawbarline.errors import ConfigurationError
from drawbarline.persistence.models.customers import IndustryRow, ShipperRow
from drawbarline.persistence.repositories.counting import count_of


def days_to_text(days: frozenset[str]) -> str:
    return ",".join(day for day in WEEKDAYS if day in days)


def days_from_text(text: str) -> frozenset[str]:
    found = [day.strip() for day in text.split(",") if day.strip()]
    unknown = set(found) - set(WEEKDAYS)
    if unknown:
        raise ConfigurationError("no such day", days=sorted(unknown))
    return frozenset(found)


def shipper_from_row(row: ShipperRow) -> Shipper:
    return Shipper(row.code, row.name, row.average_agreement)


def row_from_shipper(shipper: Shipper) -> ShipperRow:
    return ShipperRow(
        code=shipper.code,
        name=shipper.name,
        average_agreement=shipper.average_agreement,
    )


def industry_from_row(row: IndustryRow) -> Industry:
    return Industry(
        code=row.code,
        name=row.name,
        station=row.station,
        shipper=shipper_from_row(row.shipper),
        spots=row.spots,
        switched=days_from_text(row.switched),
    )


def row_from_industry(industry: Industry) -> IndustryRow:
    return IndustryRow(
        code=industry.code,
        name=industry.name,
        station=industry.station,
        shipper_code=industry.shipper.code,
        spots=industry.spots,
        switched=days_to_text(industry.switched),
    )


class CustomerRepository:
    """Shippers and their track."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def add_shipper(self, shipper: Shipper) -> Shipper:
        if self.session.get(ShipperRow, shipper.code) is not None:
            raise CustomerError("that shipper is already on the register", code=shipper.code)
        self.session.add(row_from_shipper(shipper))
        self.session.flush()
        return shipper

    def add_industry(self, industry: Industry) -> Industry:
        if self.session.get(IndustryRow, industry.code) is not None:
            raise CustomerError("that industry is already on the register", code=industry.code)
        if self.session.get(ShipperRow, industry.shipper.code) is None:
            raise CustomerError(
                "the shipper has to be on the register first",
                industry=industry.code,
                shipper=industry.shipper.code,
            )
        self.session.add(row_from_industry(industry))
        self.session.flush()
        return industry

    def shipper(self, code: str) -> Shipper:
        row = self.session.get(ShipperRow, code)
        if row is None:
            raise CustomerError("no such shipper", code=code)
        return shipper_from_row(row)

    def industry(self, code: str) -> Industry:
        row = self.session.get(IndustryRow, code)
        if row is None:
            raise CustomerError("no such industry", code=code)
        return industry_from_row(row)

    def at_station(self, station: str) -> list[Industry]:
        rows = self.session.execute(
            select(IndustryRow).where(IndustryRow.station == station).order_by(IndustryRow.code)
        ).scalars()
        return [industry_from_row(row) for row in rows]

    def shippers(self) -> list[Shipper]:
        rows = self.session.execute(select(ShipperRow).order_by(ShipperRow.code)).scalars()
        return [shipper_from_row(row) for row in rows]

    def register(self) -> CustomerRegister:
        register = CustomerRegister()
        for shipper in self.shippers():
            register.add_shipper(shipper)
        rows = self.session.execute(select(IndustryRow).order_by(IndustryRow.code)).scalars()
        for row in rows:
            register.add_industry(industry_from_row(row))
        return register

    def __len__(self) -> int:
        return count_of(self.session, IndustryRow.code)
