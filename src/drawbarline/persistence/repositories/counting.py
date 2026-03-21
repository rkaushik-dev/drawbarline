"""Counting rows without reading them.

Nine repositories each answered ``len`` by selecting a column, pulling every
value across the wire and taking the length of the list. That is correct and
it reads the whole table to produce one integer, which on the event table is
tens of thousands of values to answer how many.

One function, one aggregate, and the count comes back as a count. It is not a
speed problem anybody was going to notice on this railroad. It is nine copies
of the same wrong shape, and the next table is always the big one.
"""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import InstrumentedAttribute, Session
from sqlalchemy.sql.elements import ColumnElement


def count_of(session: Session, column: InstrumentedAttribute) -> int:
    """How many rows the table behind a column has."""
    found = session.execute(select(func.count()).select_from(column.parent)).scalar()
    return int(found or 0)


def count_where(
    session: Session, column: InstrumentedAttribute, *conditions: ColumnElement[bool]
) -> int:
    """How many rows match, for the counts a report asks for."""
    query = select(func.count()).select_from(column.parent).where(*conditions)
    found = session.execute(query).scalar()
    return int(found or 0)
