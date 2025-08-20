"""The repair pricing repository.

Both tables are small and both are read on every line of every card, so the
whole master loads into the domain objects rather than being queried job by
job. A month of cards from three roads is a few hundred lines and the master
is a few dozen rows.

The depreciation rules are the one thing in this schema that is updated in
place rather than superseded by a new row. They are not dated, because the
office manual reissues them and the old percentages are of no interest to
anybody once a card has been priced.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.errors import BillingError
from drawbarline.marks import ReportingMark
from drawbarline.persistence.models.repairs import (
    DepreciationRow,
    LaborRateRow,
    PriceLineRow,
)
from drawbarline.repairs.codes import JobCode
from drawbarline.repairs.pricing import (
    Depreciation,
    LaborRate,
    LaborRates,
    PriceLine,
    PriceMaster,
)


def price_from_row(row: PriceLineRow) -> PriceLine:
    return PriceLine(
        job=JobCode(row.job),
        effective=row.effective,
        labor_hundredths=row.labor_hundredths,
        material_cents=row.material_cents,
    )


def row_from_price(line: PriceLine) -> PriceLineRow:
    return PriceLineRow(
        job=line.job.digits,
        effective=line.effective,
        labor_hundredths=line.labor_hundredths,
        material_cents=line.material_cents,
    )


class RepairPricingRepository:
    """The price master, the depreciation rules and the labor rates."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def add_price(self, line: PriceLine) -> PriceLine:
        standing = self.session.execute(
            select(PriceLineRow).where(
                PriceLineRow.job == line.job.digits,
                PriceLineRow.effective == line.effective,
            )
        ).first()
        if standing is not None:
            raise BillingError(
                "that job is already priced from that date",
                job=str(line.job),
                effective=str(line.effective),
            )
        self.session.add(row_from_price(line))
        self.session.flush()
        return line

    def set_depreciation(self, rule: Depreciation) -> Depreciation:
        row = self.session.get(DepreciationRow, rule.family)
        if row is None:
            self.session.add(
                DepreciationRow(
                    family=rule.family,
                    percent_a_year=rule.percent_a_year,
                    floor_percent=rule.floor_percent,
                )
            )
        else:
            row.percent_a_year = rule.percent_a_year
            row.floor_percent = rule.floor_percent
        self.session.flush()
        return rule

    def file_labor(self, rate: LaborRate) -> LaborRate:
        standing = self.session.execute(
            select(LaborRateRow).where(
                LaborRateRow.road == rate.road,
                LaborRateRow.effective == rate.effective,
            )
        ).first()
        if standing is not None:
            raise BillingError("that road already filed a rate that day", road=str(rate.road))
        self.session.add(
            LaborRateRow(
                road=rate.road,
                effective=rate.effective,
                cents_an_hour=rate.cents_an_hour,
            )
        )
        self.session.flush()
        return rate

    def master(self) -> PriceMaster:
        found = PriceMaster()
        rows = self.session.execute(
            select(PriceLineRow).order_by(PriceLineRow.job, PriceLineRow.effective)
        ).scalars()
        for row in rows:
            found.add(price_from_row(row))
        rules = self.session.execute(
            select(DepreciationRow).order_by(DepreciationRow.family)
        ).scalars()
        for rule in rules:
            found.depreciate_family(
                Depreciation(rule.family, rule.percent_a_year, rule.floor_percent)
            )
        return found

    def labor_rates(self) -> LaborRates:
        found = LaborRates()
        rows = self.session.execute(
            select(LaborRateRow).order_by(LaborRateRow.road, LaborRateRow.effective)
        ).scalars()
        for row in rows:
            found.file(LaborRate(row.road, row.effective, row.cents_an_hour))
        return found

    def rate_for(self, road: ReportingMark, on: date) -> LaborRate:
        return self.labor_rates().rate_for(road, on)

    def __len__(self) -> int:
        return len(list(self.session.execute(select(PriceLineRow.id)).scalars()))
