"""The repair card repository.

Cards go in and never change. Exceptions go in against a line and are answered
once. Both of those are enforced here as well as by the table, because the
message a clerk needs is that the card is already on file, not that a unique
constraint fired.

The ninety day filing clock is enforced here as well, against the card in the
database rather than one held in memory. An exception filed late through this
path would otherwise be accepted and the register would only notice when
somebody loaded the whole thing.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.errors import BillingError, UnknownCar
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.persistence.models.cards import (
    DefectRow,
    ObjectionRow,
    RepairCardRow,
    ResolutionRow,
)
from drawbarline.persistence.models.fleet import CarRow
from drawbarline.persistence.repositories.counting import count_of
from drawbarline.repairs.card import Defect, RepairCard
from drawbarline.repairs.codes import JobCode, Responsibility, WhyMade
from drawbarline.repairs.objection import (
    Ground,
    Objection,
    Outcome,
    Resolution,
    must_file_by,
)


def defect_from_row(row: DefectRow) -> Defect:
    return Defect(
        job=JobCode(row.job),
        why=WhyMade(row.why_made),
        quantity=row.quantity,
        overridden=Responsibility[row.overridden] if row.overridden else None,
        part_years=row.part_years,
        note=row.note,
    )


def card_from_row(row: RepairCardRow) -> RepairCard:
    return RepairCard(
        number=row.number,
        car=row.car_mark,
        performed_by=row.performed_by,
        on=row.performed_on,
        station=row.station,
        defects=tuple(defect_from_row(one) for one in row.defects),
    )


def row_from_card(card: RepairCard) -> RepairCardRow:
    row = RepairCardRow(
        number=card.number,
        car_mark=card.car,
        performed_by=card.performed_by,
        performed_on=card.on,
        station=card.station,
    )
    row.defects = [
        DefectRow(
            position=at,
            job=one.job.digits,
            why_made=one.why.digits,
            quantity=one.quantity,
            overridden=one.overridden.name if one.overridden else None,
            part_years=one.part_years,
            note=one.note,
        )
        for at, one in enumerate(card.defects)
    ]
    return row


def objection_from_row(row: ObjectionRow) -> Objection:
    return Objection(
        card_number=row.card_number,
        line=row.line,
        ground=Ground[row.ground],
        disputed=row.disputed,
        filed_on=row.filed_on,
        note=row.note,
    )


class CardRepository:
    """Every repair card and every exception filed against one."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def file(self, card: RepairCard) -> RepairCard:
        if self.session.get(RepairCardRow, card.number) is not None:
            raise BillingError("that card is already on file", number=card.number)
        if self.session.get(CarRow, card.car) is None:
            raise UnknownCar("no record of that car", car=str(card.car))
        self.session.add(row_from_card(card))
        self.session.flush()
        return card

    def get(self, number: int) -> RepairCard:
        row = self.session.get(RepairCardRow, number)
        if row is None:
            raise BillingError("no such card", number=number)
        return card_from_row(row)

    def for_car(self, car: CarMark) -> list[RepairCard]:
        rows = self.session.execute(
            select(RepairCardRow)
            .where(RepairCardRow.car_mark == car)
            .order_by(RepairCardRow.performed_on, RepairCardRow.number)
        ).scalars()
        return [card_from_row(row) for row in rows]

    def by_road(self, road: ReportingMark) -> list[RepairCard]:
        rows = self.session.execute(
            select(RepairCardRow)
            .where(RepairCardRow.performed_by == road)
            .order_by(RepairCardRow.number)
        ).scalars()
        return [card_from_row(row) for row in rows]

    def between(self, first: date, last: date) -> list[RepairCard]:
        rows = self.session.execute(
            select(RepairCardRow)
            .where(
                RepairCardRow.performed_on >= first,
                RepairCardRow.performed_on <= last,
            )
            .order_by(RepairCardRow.performed_on, RepairCardRow.number)
        ).scalars()
        return [card_from_row(row) for row in rows]

    def _objection_row(self, card_number: int, line: int) -> ObjectionRow | None:
        return self.session.execute(
            select(ObjectionRow).where(
                ObjectionRow.card_number == card_number, ObjectionRow.line == line
            )
        ).scalar_one_or_none()

    def take_exception(self, objection: Objection) -> Objection:
        card = self.get(objection.card_number)
        if objection.line >= len(card.defects):
            raise BillingError("that card has no such line", card=card.number, line=objection.line)
        if objection.filed_on > must_file_by(card.on):
            raise BillingError(
                "that exception is out of time",
                card=card.number,
                by=str(must_file_by(card.on)),
            )
        if self._objection_row(objection.card_number, objection.line) is not None:
            raise BillingError(
                "that line is already under exception",
                card=objection.card_number,
                line=objection.line,
            )
        self.session.add(
            ObjectionRow(
                card_number=objection.card_number,
                line=objection.line,
                ground=objection.ground.name,
                disputed=objection.disputed,
                filed_on=objection.filed_on,
                note=objection.note,
            )
        )
        self.session.flush()
        return objection

    def answer(self, objection: Objection, resolution: Resolution) -> Resolution:
        row = self._objection_row(objection.card_number, objection.line)
        if row is None:
            raise BillingError(
                "no exception is on file for that line",
                card=objection.card_number,
                line=objection.line,
            )
        if row.resolution is not None:
            raise BillingError(
                "that exception is already settled",
                card=objection.card_number,
                line=objection.line,
            )
        row.resolution = ResolutionRow(outcome=resolution.outcome.name, allowed=resolution.allowed)
        self.session.flush()
        return resolution

    def open_objections(self) -> list[Objection]:
        rows = self.session.execute(
            select(ObjectionRow).order_by(ObjectionRow.card_number, ObjectionRow.line)
        ).scalars()
        return [objection_from_row(row) for row in rows if row.resolution is None]

    def outcome_of(self, card_number: int, line: int) -> Outcome | None:
        row = self._objection_row(card_number, line)
        if row is None or row.resolution is None:
            return None
        return Outcome[row.resolution.outcome]

    def __len__(self) -> int:
        return count_of(self.session, RepairCardRow.number)
