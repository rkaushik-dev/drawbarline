"""The mapping floor.

One declarative base, one place that builds an engine, and one place that
hands out sessions. Nothing in the domain imports any of it, which is the
point: the fleet register and the car hire accrual work on objects and know
nothing about a database, and everything that does know is under this package.

Sqlite is what the office runs on, on a file on the server in the general
office at Powers, and foreign keys are off by default there. Turning them on
per connection is not optional. Without it a car hire posting can point at a
road that does not exist and nothing complains until a statement comes out
short.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

from sqlalchemy import Engine, create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import StaticPool

from drawbarline.errors import ConfigurationError

IN_MEMORY = "sqlite+pysqlite:///:memory:"


class Base(DeclarativeBase):
    """Every mapped class descends from this."""


def make_engine(url: str = IN_MEMORY, *, echo: bool = False) -> Engine:
    """Build an engine with the settings this application needs, not the defaults."""
    if not url.strip():
        raise ConfigurationError("a database url is required")
    settings: dict[str, Any] = {"echo": echo, "future": True}
    if url == IN_MEMORY:
        # A memory database lives inside its connection. The default pool opens
        # a new one per checkout, so the schema built on the first connection is
        # gone by the second and every read finds an empty database.
        settings["poolclass"] = StaticPool
        settings["connect_args"] = {"check_same_thread": False}
    engine = create_engine(url, **settings)
    if url.startswith("sqlite"):
        _enforce_foreign_keys(engine)
    return engine


def _enforce_foreign_keys(engine: Engine) -> None:
    @event.listens_for(engine, "connect")
    def _on_connect(connection: Any, record: Any) -> None:
        cursor = connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


def create_all(engine: Engine) -> None:
    """Build the schema. Alembic owns this everywhere except a test."""
    Base.metadata.create_all(engine)


def drop_all(engine: Engine) -> None:
    Base.metadata.drop_all(engine)


def make_sessions(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False, future=True)


@contextmanager
def opened(engine: Engine) -> Iterator[Session]:
    """A session that commits on the way out and rolls back if anything raises."""
    session = make_sessions(engine)()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def table_names() -> list[str]:
    """Every table on the metadata, which a migration check compares against."""
    return sorted(Base.metadata.tables)
