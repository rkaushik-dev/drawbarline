"""The interchange agreement repository.

The kind letters go in and out through one pair of functions so the packed
column is understood in one place, and every letter is checked against the
published kinds on the way back. A hand edited row with a letter nobody
publishes would otherwise become an agreement that silently takes nothing.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.agreements import AgreementError, InterchangeAgreement
from drawbarline.fleet.aar import CarKind
from drawbarline.marks import ReportingMark
from drawbarline.persistence.models.agreements import AgreementRow
from drawbarline.persistence.repositories.counting import count_of


def kinds_to_text(kinds: frozenset[CarKind]) -> str:
    return "".join(one.letter for one in CarKind if one in kinds)


def kinds_from_text(text: str) -> frozenset[CarKind]:
    return frozenset(CarKind.from_letter(letter) for letter in text.strip())


def agreement_from_row(row: AgreementRow) -> InterchangeAgreement:
    return InterchangeAgreement(
        road=row.road,
        junction=row.junction,
        effective=row.effective,
        kinds=kinds_from_text(row.kinds),
        daily_cap=row.daily_cap,
        opens=row.opens,
        closes=row.closes,
        hazardous=row.hazardous,
        expires=row.expires,
    )


def row_from_agreement(agreement: InterchangeAgreement) -> AgreementRow:
    return AgreementRow(
        road=agreement.road,
        junction=agreement.junction,
        effective=agreement.effective,
        kinds=kinds_to_text(agreement.kinds),
        daily_cap=agreement.daily_cap,
        opens=agreement.opens,
        closes=agreement.closes,
        hazardous=agreement.hazardous,
        expires=agreement.expires,
    )


class AgreementRepository:
    """Every interchange agreement the road has signed."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def sign(self, agreement: InterchangeAgreement) -> InterchangeAgreement:
        standing = self.session.execute(
            select(AgreementRow).where(
                AgreementRow.road == agreement.road,
                AgreementRow.junction == agreement.junction,
                AgreementRow.effective == agreement.effective,
            )
        ).first()
        if standing is not None:
            raise AgreementError("that agreement is already signed", junction=agreement.code)
        self.session.add(row_from_agreement(agreement))
        self.session.flush()
        return agreement

    def at(self, junction: str, road: ReportingMark, on: date) -> InterchangeAgreement:
        """The agreement in force at one junction with one road."""
        rows = self.session.execute(
            select(AgreementRow)
            .where(
                AgreementRow.junction == junction,
                AgreementRow.road == road,
                AgreementRow.effective <= on,
            )
            .order_by(AgreementRow.effective.desc())
        ).scalars()
        for row in rows:
            found = agreement_from_row(row)
            if found.in_force_on(on):
                return found
        raise AgreementError(
            "no agreement is in force there",
            junction=f"{junction}/{road}",
            on=str(on),
        )

    def in_force_on(self, day: date) -> list[InterchangeAgreement]:
        rows = self.session.execute(
            select(AgreementRow).order_by(AgreementRow.junction, AgreementRow.road)
        ).scalars()
        return [one for one in (agreement_from_row(row) for row in rows) if one.in_force_on(day)]

    def all(self) -> list[InterchangeAgreement]:
        rows = self.session.execute(
            select(AgreementRow).order_by(
                AgreementRow.junction, AgreementRow.road, AgreementRow.effective
            )
        ).scalars()
        return [agreement_from_row(row) for row in rows]

    def __len__(self) -> int:
        return count_of(self.session, AgreementRow.id)
