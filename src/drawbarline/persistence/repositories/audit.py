"""The audit repository.

Append only, in the strongest sense available: there is no update and no
delete on it at all. An entry written in error is answered with another entry,
the same way a posting is.
"""

from __future__ import annotations

from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.audit import Action, AuditTrail, Entry
from drawbarline.persistence.models.audit import AuditRow


def entry_from_row(row: AuditRow) -> Entry:
    return Entry(
        at=row.at,
        actor=row.actor,
        action=Action[row.action],
        subject=row.subject,
        detail=row.detail,
    )


def row_from_entry(entry: Entry) -> AuditRow:
    return AuditRow(
        at=entry.at,
        actor=entry.actor,
        action=entry.action.name,
        subject=entry.subject,
        detail=entry.detail,
    )


class AuditRepository:
    """Every decision, written down."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def record(
        self,
        actor: str,
        action: Action,
        subject: str,
        detail: str = "",
        at: datetime | None = None,
    ) -> Entry:
        entry = Entry(
            at=at or datetime.now(UTC),
            actor=actor,
            action=action,
            subject=subject,
            detail=detail,
        )
        self.session.add(row_from_entry(entry))
        self.session.flush()
        return entry

    def entries(self, limit: int | None = None) -> list[Entry]:
        query = select(AuditRow).order_by(AuditRow.at.desc(), AuditRow.id.desc())
        if limit is not None:
            query = query.limit(limit)
        return [entry_from_row(row) for row in self.session.execute(query).scalars()]

    def by_actor(self, actor: str) -> list[Entry]:
        rows = self.session.execute(
            select(AuditRow).where(AuditRow.actor == actor.strip()).order_by(AuditRow.at)
        ).scalars()
        return [entry_from_row(row) for row in rows]

    def about(self, subject: str) -> list[Entry]:
        rows = self.session.execute(
            select(AuditRow).where(AuditRow.subject == subject).order_by(AuditRow.at)
        ).scalars()
        return [entry_from_row(row) for row in rows]

    def of_action(self, action: Action) -> list[Entry]:
        rows = self.session.execute(
            select(AuditRow).where(AuditRow.action == action.name).order_by(AuditRow.at)
        ).scalars()
        return [entry_from_row(row) for row in rows]

    def trail(self) -> AuditTrail:
        found = AuditTrail()
        for entry in self.entries():
            found.record(entry.actor, entry.action, entry.subject, entry.detail, at=entry.at)
        return found

    def __len__(self) -> int:
        return len(list(self.session.execute(select(AuditRow.id)).scalars()))
