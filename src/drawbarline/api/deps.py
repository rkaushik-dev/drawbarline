"""What a route needs to reach the books.

One engine for the process, one unit of work a request, and the unit of work
commits only if the route returns without raising. That is the same rule the
command line follows and for the same reason: a route that fails half way
should leave the month exactly as it was.
"""

from __future__ import annotations

from collections.abc import Iterator

from fastapi import Request
from sqlalchemy import Engine

from drawbarline.errors import ConfigurationError
from drawbarline.persistence.uow import UnitOfWork

ENGINE_KEY = "drawbarline_engine"


def engine_of(request: Request) -> Engine:
    engine = getattr(request.app.state, ENGINE_KEY, None)
    if engine is None:
        raise ConfigurationError("the application has no database")
    return engine


def unit_of_work(request: Request) -> Iterator[UnitOfWork]:
    """A unit of work for one request, committed on the way out."""
    with UnitOfWork(engine_of(request)) as work:
        yield work
        work.commit()


def read_only(request: Request) -> Iterator[UnitOfWork]:
    """A unit of work for a route that only reads, which never commits."""
    with UnitOfWork(engine_of(request)) as work:
        yield work
