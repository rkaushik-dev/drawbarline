"""The audit table.

One row an entry and nothing else. There is no foreign key on the subject
because a subject is a car one day and a period the next, and a column that
points at four different tables depending on a neighbouring column is worse
than a string.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Index, String
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.limits import ACTOR, DETAIL, SUBJECT
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import Utc


class AuditRow(Base):
    """One decision a person made."""

    __tablename__ = "audit"
    __table_args__ = (
        Index("ix_audit_actor_at", "actor", "at"),
        Index("ix_audit_subject", "subject"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    at: Mapped[datetime] = mapped_column(Utc, index=True)
    actor: Mapped[str] = mapped_column(String(ACTOR))
    action: Mapped[str] = mapped_column(String(24), index=True)
    subject: Mapped[str] = mapped_column(String(SUBJECT))
    detail: Mapped[str] = mapped_column(String(DETAIL), default="")

    def __repr__(self) -> str:
        return f"<AuditRow {self.actor} {self.action} {self.subject}>"
