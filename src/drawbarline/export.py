"""Handing a month to somebody who is not this application.

The auditors want a month, the connections occasionally want their part of
one, and neither of them is going to install this. So a month goes out as
json: the postings, the statements, the demurrage invoices and the events
behind them, in one file, with the figures as integers in the units the
package uses rather than as formatted money.

Nothing is computed on the way out. Every figure is read back off the books,
which means an export and the statement that went out in the post carry the
same numbers, and an export of a month that was reopened shows where it ended
rather than where it was first agreed.

The shape is versioned. Somebody will write a reader for this and the reader
will outlive whoever wrote it, so the file says what it is.
"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from drawbarline.persistence.uow import UnitOfWork
from drawbarline.reports.ledger import balances, trial_balance
from drawbarline.settlement.period import Period
from drawbarline.settlement.statement import cut_all

SHAPE = "drawbarline.month"
VERSION = 1


def postings_of(work: UnitOfWork, period: Period) -> list[dict[str, Any]]:
    return [
        {
            "road": str(one.road),
            "kind": one.kind.name.lower(),
            "cents": one.amount.cents,
            "on": str(one.on),
            "reference": one.reference,
        }
        for one in work.ledger.postings()
        if one.period == period
    ]


def statements_of(work: UnitOfWork, period: Period) -> list[dict[str, Any]]:
    ledger = work.ledger.ledger()
    found = cut_all(ledger, period, first_number=1, issued=period.last_day)
    return [
        {
            "road": str(one.road),
            "opening_cents": one.opening.cents,
            "closing_cents": one.closing.cents,
            "direction": one.direction(),
            "lines": [
                {"kind": line.kind.name.lower(), "cents": line.amount.cents} for line in one.lines
            ],
        }
        for one in found
    ]


def events_of(work: UnitOfWork, period: Period) -> list[dict[str, Any]]:
    return [
        {
            "car": str(one.car),
            "kind": str(one.kind),
            "moment": one.when.isoformat(),
            "station": one.station,
            "road": str(one.road) if one.road else None,
            "industry": one.industry,
            "waybill": one.waybill,
            "tenths": one.tenths,
        }
        for one in work.events.during(period.window)
    ]


def audit_of(work: UnitOfWork) -> list[dict[str, Any]]:
    return [
        {
            "at": one.at.isoformat(),
            "actor": one.actor,
            "action": one.action.name.lower(),
            "subject": one.subject,
            "detail": one.detail,
        }
        for one in work.audit.entries()
    ]


def month(work: UnitOfWork, period: Period, *, at: datetime | None = None) -> dict[str, Any]:
    """One month, whole, as plain data."""
    ledger = work.ledger.ledger()
    lines = trial_balance(ledger, period)
    return {
        "shape": SHAPE,
        "version": VERSION,
        "taken_at": (at or datetime.now(UTC)).isoformat(),
        "period": period.label,
        "closed": not work.periods.is_open(period),
        "balances": balances(lines, ledger, period),
        "postings": postings_of(work, period),
        "statements": statements_of(work, period),
        "events": events_of(work, period),
        "audit": audit_of(work),
    }


def counts(bundle: dict[str, Any]) -> dict[str, int]:
    """How much is in a bundle, for anybody checking one arrived whole."""
    return {name: len(bundle[name]) for name in ("postings", "statements", "events", "audit")}
