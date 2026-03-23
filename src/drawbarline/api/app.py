"""Building the application.

The engine is handed in rather than built here, so a test stands the whole
thing up against a database in a temporary directory and the office stands it
up against the file on the share. Nothing below this line reads an environment
variable. :func:`app_from` is the one that does, and it is what a server
process calls.

There is no authentication on it. The general office network is the boundary
and this application sits behind it, which is a decision worth writing down
because it is the kind of thing somebody assumes was an oversight.
"""

from __future__ import annotations

from fastapi import Depends, FastAPI
from sqlalchemy import Engine

from drawbarline import __version__
from drawbarline.api.deps import ENGINE_KEY, read_only
from drawbarline.api.errors import install
from drawbarline.api.routes import (
    audit,
    events,
    fleet,
    operations,
    orders,
    repairs,
    reports,
    settlement,
)
from drawbarline.config import Settings, from_environment
from drawbarline.logs import configure
from drawbarline.persistence.base import make_engine
from drawbarline.persistence.uow import UnitOfWork

TITLE = "drawbarline"
SUMMARY = "Car hire, demurrage and interchange settlement"


def make_app(engine: Engine) -> FastAPI:
    """Stand the application up against one database."""
    app = FastAPI(title=TITLE, summary=SUMMARY, version=__version__)
    setattr(app.state, ENGINE_KEY, engine)
    install(app)
    app.include_router(fleet.router)
    app.include_router(events.router)
    app.include_router(settlement.router)
    app.include_router(reports.router)
    app.include_router(repairs.router)
    app.include_router(operations.router)
    app.include_router(audit.router)
    app.include_router(orders.router)

    @app.get("/health", tags=["service"])
    def health() -> dict[str, str]:
        """Whether the process is up. It says nothing about the database."""
        return {"status": "up", "version": __version__}

    @app.get("/books", tags=["service"])
    def books(work: UnitOfWork = Depends(read_only)) -> dict[str, int]:
        """How much is on the books, which does touch the database."""
        return {
            "cars": len(work.fleet),
            "events": len(work.events),
            "postings": len(work.ledger),
            "closed_months": len(work.periods.closed_periods()),
        }

    return app


def app_from(settings: Settings | None = None) -> FastAPI:
    """Stand the application up from settings, which is what a server does."""
    found = settings or from_environment()
    configure(found)
    return make_app(make_engine(found.url, echo=found.echo_sql))
