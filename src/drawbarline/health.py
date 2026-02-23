"""Whether a set of books is ready to be worked.

Half the support calls this office gets are a database that was built but
never had its filings loaded, so a close runs, produces nothing, and shuts a
month that had traffic in it. Nothing about that is an error anywhere: every
step did what it was told.

So this asks the questions somebody would ask before running a close, and
answers them one at a time. Each check knows what it is for and says what to
do about it, because a check that reports a fault and not a remedy is a check
somebody has to interpret.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from drawbarline.errors import DrawbarlineError
from drawbarline.persistence.base import Base
from drawbarline.persistence.numbering import SERIES
from drawbarline.persistence.uow import UnitOfWork


class Level(Enum):
    """How much a finding matters."""

    GOOD = "ok"
    WARN = "warn"
    BAD = "bad"

    @property
    def stops_a_close(self) -> bool:
        return self is Level.BAD


@dataclass(frozen=True, slots=True)
class Finding:
    """One thing that was checked."""

    check: str
    level: Level
    detail: str
    remedy: str = ""

    def __str__(self) -> str:
        tail = f" ({self.remedy})" if self.remedy and self.level is not Level.GOOD else ""
        return f"{self.level.value:<5} {self.check:<18} {self.detail}{tail}"


def schema(work: UnitOfWork) -> Finding:
    from sqlalchemy import inspect

    found = set(inspect(work.session.get_bind()).get_table_names())
    missing = set(Base.metadata.tables) - found
    if missing:
        return Finding(
            "schema",
            Level.BAD,
            f"{len(missing)} tables missing",
            "run alembic upgrade head",
        )
    return Finding("schema", Level.GOOD, f"{len(found)} tables")


def counters(work: UnitOfWork) -> Finding:
    open_series = set(work.numbering.open_series())
    missing = set(SERIES) - open_series
    if missing:
        return Finding(
            "numbering",
            Level.BAD,
            f"{sorted(missing)} not opened",
            "run init on a fresh database",
        )
    return Finding("numbering", Level.GOOD, f"{len(open_series)} series open")


def rates(work: UnitOfWork) -> Finding:
    filed = len(work.rates)
    if not filed:
        return Finding("car hire rates", Level.BAD, "none filed", "reference rates <sheet>")
    return Finding("car hire rates", Level.GOOD, f"{filed} bands")


def tariffs(work: UnitOfWork, on: date) -> Finding:
    try:
        found = work.tariffs.in_effect_on(on)
    except DrawbarlineError:
        return Finding("demurrage tariff", Level.BAD, "none in force", "reference tariff ...")
    return Finding("demurrage tariff", Level.GOOD, f"tariff {found.code}")


def customers(work: UnitOfWork) -> Finding:
    tracks = len(work.customers)
    if not tracks:
        return Finding(
            "industry track",
            Level.WARN,
            "none filed",
            "reference industries <sheet>",
        )
    return Finding("industry track", Level.GOOD, f"{tracks} tracks")


def fleet(work: UnitOfWork) -> Finding:
    cars = len(work.fleet)
    if not cars:
        return Finding("fleet", Level.WARN, "no cars on the register", "fleet add ...")
    return Finding("fleet", Level.GOOD, f"{cars} cars")


def agreements(work: UnitOfWork, on: date) -> Finding:
    live = len(work.agreements.in_force_on(on))
    if not live:
        return Finding("agreements", Level.WARN, "none in force", "agreement sign ...")
    return Finding("agreements", Level.GOOD, f"{live} in force")


def examine(work: UnitOfWork, on: date) -> list[Finding]:
    """Every check, in the order somebody would ask them.

    The schema check comes first and stops the rest, because every check after
    it reads a table. Running them anyway turns one clear finding into six
    operational errors that all say the same thing.
    """
    built = schema(work)
    if built.level.stops_a_close:
        return [built]
    return [
        built,
        counters(work),
        rates(work),
        tariffs(work, on),
        customers(work),
        fleet(work),
        agreements(work, on),
    ]


def ready(findings: list[Finding]) -> bool:
    """Whether a close would produce anything worth sending out."""
    return not any(one.level.stops_a_close for one in findings)
