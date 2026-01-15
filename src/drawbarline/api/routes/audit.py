"""The audit routes.

Reading only. There is no route that writes an audit entry, because an entry
written on its own says a decision was made without the decision having been
made. Entries are written by the service layer as a consequence of the thing
they record, and that is the only way one can appear.
"""

from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from drawbarline.api.deps import read_only
from drawbarline.audit import Action, Entry
from drawbarline.errors import ConfigurationError
from drawbarline.persistence.uow import UnitOfWork

router = APIRouter(prefix="/audit", tags=["audit"])

MOST = 500


class EntryOut(BaseModel):
    at: datetime
    actor: str
    action: str
    subject: str
    detail: str

    @classmethod
    def of(cls, entry: Entry) -> EntryOut:
        return cls(
            at=entry.at,
            actor=entry.actor,
            action=entry.action.name.lower(),
            subject=entry.subject,
            detail=entry.detail,
        )


@router.get("", response_model=list[EntryOut])
def entries(
    actor: str | None = None,
    action: str | None = None,
    subject: str | None = None,
    limit: int = Query(default=50, ge=1, le=MOST),
    work: UnitOfWork = Depends(read_only),
) -> list[EntryOut]:
    """Decisions, newest first, filtered by whichever of the three is given."""
    if actor:
        found = work.audit.by_actor(actor)
    elif action:
        try:
            wanted = Action[action.strip().upper()]
        except KeyError as bad:
            known = ", ".join(one.name.lower() for one in Action)
            raise ConfigurationError("no such action", given=action, known=known) from bad
        found = work.audit.of_action(wanted)
    elif subject:
        found = work.audit.about(subject)
    else:
        found = work.audit.entries(limit)
    return [EntryOut.of(one) for one in found[:limit]]


@router.get("/actions", response_model=list[str])
def actions() -> list[str]:
    """Every decision this application records."""
    return [one.name.lower() for one in Action]
