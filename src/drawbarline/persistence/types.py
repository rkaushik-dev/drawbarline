"""Column types that store what the domain actually means.

The domain holds money in cents, weights in pounds and marks as small frozen
objects. A column typed as a plain integer would store the same bytes, but
every read would hand back an int and every call site would have to remember
which unit it was in. That memory is exactly what these types exist to take
away.

The awkward one is time. Sqlite does not store an offset, so a datetime
written as aware comes back naive and utterly plausible. Rather than trust
that, the type writes utc and stamps utc back on the way out, and refuses a
naive value going in so the mistake is caught at the write rather than
discovered a month later in a report.
"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from sqlalchemy import DateTime, Integer, String
from sqlalchemy.engine import Dialect
from sqlalchemy.types import TypeDecorator

from drawbarline.errors import ConfigurationError
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.money import Money
from drawbarline.weight import Weight


class MoneyCents(TypeDecorator[Money]):
    """Money, stored as a signed count of cents."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Money | None, dialect: Dialect) -> int | None:
        return None if value is None else value.cents

    def process_result_value(self, value: Any, dialect: Dialect) -> Money | None:
        return None if value is None else Money(int(value))


class WeightPounds(TypeDecorator[Weight]):
    """A weight, stored as whole pounds."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Weight | None, dialect: Dialect) -> int | None:
        return None if value is None else value.pounds

    def process_result_value(self, value: Any, dialect: Dialect) -> Weight | None:
        return None if value is None else Weight(int(value))


class LengthInches(TypeDecorator[Length]):
    """A length, stored as whole inches."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Length | None, dialect: Dialect) -> int | None:
        return None if value is None else value.inches

    def process_result_value(self, value: Any, dialect: Dialect) -> Length | None:
        return None if value is None else Length(int(value))


class MarkText(TypeDecorator[ReportingMark]):
    """A reporting mark, stored as its letters."""

    impl = String(4)
    cache_ok = True

    def process_bind_param(self, value: ReportingMark | None, dialect: Dialect) -> str | None:
        return None if value is None else value.letters

    def process_result_value(self, value: Any, dialect: Dialect) -> ReportingMark | None:
        return None if value is None else ReportingMark(str(value))


class CarMarkText(TypeDecorator[CarMark]):
    """A car, stored as the marks and number a clerk would write."""

    impl = String(11)
    cache_ok = True

    def process_bind_param(self, value: CarMark | None, dialect: Dialect) -> str | None:
        return None if value is None else str(value)

    def process_result_value(self, value: Any, dialect: Dialect) -> CarMark | None:
        return None if value is None else CarMark.parse(str(value))


class TypeCodeText(TypeDecorator[CarTypeCode]):
    """An aar car type code."""

    impl = String(4)
    cache_ok = True

    def process_bind_param(self, value: CarTypeCode | None, dialect: Dialect) -> str | None:
        return None if value is None else value.code

    def process_result_value(self, value: Any, dialect: Dialect) -> CarTypeCode | None:
        return None if value is None else CarTypeCode(str(value))


class DesignationText(TypeDecorator[MechanicalDesignation]):
    """A mechanical designation."""

    impl = String(4)
    cache_ok = True

    def process_bind_param(
        self, value: MechanicalDesignation | None, dialect: Dialect
    ) -> str | None:
        return None if value is None else value.letters

    def process_result_value(self, value: Any, dialect: Dialect) -> MechanicalDesignation | None:
        return None if value is None else MechanicalDesignation(str(value))


class Utc(TypeDecorator[datetime]):
    """An instant, written and read as utc, refusing anything naive."""

    impl = DateTime(timezone=True)
    cache_ok = True

    def process_bind_param(self, value: datetime | None, dialect: Dialect) -> datetime | None:
        if value is None:
            return None
        if value.tzinfo is None or value.utcoffset() is None:
            raise ConfigurationError("a stored moment carries its zone")
        return value.astimezone(UTC)

    def process_result_value(self, value: Any, dialect: Dialect) -> datetime | None:
        if value is None:
            return None
        if value.tzinfo is None:
            return value.replace(tzinfo=UTC)
        return value.astimezone(UTC)
