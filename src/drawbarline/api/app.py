"""Building the application.

The engine is handed in rather than built here, so a test stands the whole
thing up against a database in a temporary directory and the office stands it
up against the file on the share. Nothing reads an environment variable below
this line.

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
from drawbarline.persistence.uow import UnitOfWork

TITLE = "drawbarline"
SUMMARY = "Car hire, demurrage and interchange settlement"


def make_app(engine: Engine) -> FastAPI:
    """Stand the application up against one database."""
    app = FastAPI(title=TITLE, summary=SUMMARY, version=__version__)
    setattr(app.state, ENGINE_KEY, engine)
    install(app)

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
