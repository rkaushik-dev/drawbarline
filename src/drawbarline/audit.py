"""Who did what, and when they did it.

Most of this application is already an audit trail, because the event log is
never edited and the ledger is never updated. What is missing from those is
the person. An event says a car was received at Escanaba at seven in the
morning. It does not say who keyed it, and when a month has to be reopened the
first question is always who closed it and on whose say so.

So this is a second, thinner log, and it records only the things a person
decides: closing a month, reopening one, retiring a car, lifting an embargo,
allowing an exception. It does not record reads, because a trail that records
reads is a trail nobody can find anything in.

The actor is a string and it is not checked against anything. There is no user
table on this railroad. It is the initials on the bottom of the form, and the
value of it is that somebody can be asked.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from enum import Enum

from drawbarline.clock import require_aware
from drawbarline.errors import DrawbarlineError

ACTOR_LIMIT = 32
DETAIL_LIMIT = 200


class AuditError(DrawbarlineError):
    """An entry cannot be written as it stands."""

    code = "audit"


class Action(Enum):
    """The decisions worth remembering a person made."""

    CLOSED_PERIOD = "closed a period"
    REOPENED_PERIOD = "reopened a period"
    RETIRED_CAR = "retired a car"
    CORRECTED_CAR = "corrected an equipment record"
    LEASED_CAR = "filed a lease"
    ISSUED_EMBARGO = "issued an embargo"
    LIFTED_EMBARGO = "lifted an embargo"
    FILED_CARD = "filed a repair card"
    ANSWERED_EXCEPTION = "answered an exception"
    LOADED_SHEET = "loaded an interchange sheet"
    PUBLISHED_TARIFF = "published a tariff"

    @property
    def text(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Entry:
    """One decision, one person, one moment."""

    at: datetime
    actor: str
    action: Action
    subject: str
    detail: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "at", require_aware(self.at, "at"))
        cleaned = self.actor.strip()
        if not cleaned:
            raise AuditError("an entry names who did it", action=self.action.name)
        if len(cleaned) > ACTOR_LIMIT:
            raise AuditError("that is a signature, not initials", actor=self.actor)
        if len(self.detail) > DETAIL_LIMIT:
            raise AuditError("that detail is too long to carry", length=len(self.detail))
        object.__setattr__(self, "actor", cleaned)
        if not self.subject.strip():
            raise AuditError("an entry names what it is about", action=self.action.name)

    def __str__(self) -> str:
        tail = f" ({self.detail})" if self.detail else ""
        return f"{self.at:%Y-%m-%d %H:%M}Z {self.actor} {self.action.text} {self.subject}{tail}"


class AuditTrail:
    """Every decision, in the order they were made."""

    def __init__(self) -> None:
        self._entries: list[Entry] = []

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
        self._entries.append(entry)
        return entry

    @property
    def entries(self) -> list[Entry]:
        return sorted(self._entries, key=lambda one: (one.at, one.action.name))

    def by_actor(self, actor: str) -> list[Entry]:
        return [one for one in self.entries if one.actor == actor.strip()]

    def of_action(self, action: Action) -> list[Entry]:
        return [one for one in self.entries if one.action is action]

    def about(self, subject: str) -> list[Entry]:
        return [one for one in self.entries if one.subject == subject]

    def since(self, moment: datetime) -> list[Entry]:
        cut = require_aware(moment, "since")
        return [one for one in self.entries if one.at >= cut]

    def actors(self) -> list[str]:
        return sorted({one.actor for one in self._entries})

    def __len__(self) -> int:
        return len(self._entries)
