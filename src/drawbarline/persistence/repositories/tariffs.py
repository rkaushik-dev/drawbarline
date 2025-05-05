"""The tariff repository.

Publishing a tariff writes the header and its steps together, and reading one
back builds the domain object, which is where the rule that only the last step
runs to the end is enforced. A tariff assembled by hand in the database with
two open ended steps fails at the read, loudly, rather than pricing a month
wrongly and quietly.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.demurrage.average import AverageAgreement
from drawbarline.demurrage.tariff import Step, Tariff, TariffBook
from drawbarline.errors import TariffError
from drawbarline.persistence.models.tariffs import (
    AverageAgreementRow,
    TariffRow,
    TariffStepRow,
)


def tariff_from_row(row: TariffRow) -> Tariff:
    return Tariff(
        code=row.code,
        effective=row.effective,
        free_days_loaded=row.free_days_loaded,
        free_days_empty=row.free_days_empty,
        steps=tuple(Step(one.through, one.cents) for one in row.steps),
    )


def row_from_tariff(tariff: Tariff) -> TariffRow:
    row = TariffRow(
        code=tariff.code,
        effective=tariff.effective,
        free_days_loaded=tariff.free_days_loaded,
        free_days_empty=tariff.free_days_empty,
    )
    row.steps = [
        TariffStepRow(position=at, through=step.through, cents=step.cents)
        for at, step in enumerate(tariff.steps)
    ]
    return row


def agreement_from_row(row: AverageAgreementRow) -> AverageAgreement:
    return AverageAgreement(
        shipper=row.shipper_code,
        daily_cents=row.daily_cents,
        effective=row.effective,
        expires=row.expires,
    )


class TariffRepository:
    """Every published tariff and every signed agreement."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def publish(self, tariff: Tariff) -> Tariff:
        if self.session.get(TariffRow, tariff.code) is not None:
            raise TariffError("that tariff is already published", code=tariff.code)
        standing = self.session.execute(
            select(TariffRow).where(TariffRow.effective == tariff.effective)
        ).first()
        if standing is not None:
            raise TariffError(
                "a tariff already takes effect that day",
                effective=str(tariff.effective),
            )
        self.session.add(row_from_tariff(tariff))
        self.session.flush()
        return tariff

    def tariff(self, code: str) -> Tariff:
        row = self.session.get(TariffRow, code)
        if row is None:
            raise TariffError("no such tariff", code=code)
        return tariff_from_row(row)

    def in_effect_on(self, day: date) -> Tariff:
        found = self.session.execute(
            select(TariffRow).where(TariffRow.effective <= day).order_by(TariffRow.effective.desc())
        ).scalars()
        row = found.first()
        if row is None:
            raise TariffError("no tariff was in effect", on=str(day))
        return tariff_from_row(row)

    def book(self) -> TariffBook:
        book = TariffBook()
        rows = self.session.execute(select(TariffRow).order_by(TariffRow.effective)).scalars()
        for row in rows:
            book.publish(tariff_from_row(row))
        return book

    def sign(self, agreement: AverageAgreement) -> AverageAgreement:
        standing = self.session.execute(
            select(AverageAgreementRow).where(
                AverageAgreementRow.shipper_code == agreement.shipper,
                AverageAgreementRow.effective == agreement.effective,
            )
        ).first()
        if standing is not None:
            raise TariffError("that agreement is already signed", shipper=agreement.shipper)
        self.session.add(
            AverageAgreementRow(
                shipper_code=agreement.shipper,
                daily_cents=agreement.daily_cents,
                effective=agreement.effective,
                expires=agreement.expires,
            )
        )
        self.session.flush()
        return agreement

    def agreement_for(self, shipper: str, day: date) -> AverageAgreement:
        rows = self.session.execute(
            select(AverageAgreementRow)
            .where(AverageAgreementRow.shipper_code == shipper)
            .order_by(AverageAgreementRow.effective)
        ).scalars()
        for row in rows:
            found = agreement_from_row(row)
            if found.covers(day):
                return found
        raise TariffError("no agreement was in force", shipper=shipper, on=str(day))

    def agreements(self) -> list[AverageAgreement]:
        rows = self.session.execute(
            select(AverageAgreementRow).order_by(
                AverageAgreementRow.shipper_code, AverageAgreementRow.effective
            )
        ).scalars()
        return [agreement_from_row(row) for row in rows]
