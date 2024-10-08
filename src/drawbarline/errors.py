"""Every refusal the package makes travels as one of these.

The rule is that a caller can catch :class:`DrawbarlineError` and be sure it
caught something we raised on purpose. A ``ValueError`` escaping from here is a
bug in the package, not a message about the data.

Each error carries a short stable ``code`` and a mapping of the values that
caused it. The code is what a report groups on, so it never changes once an
exception class is in use. The context is what an operator reads.
"""

from __future__ import annotations

from typing import Any


class DrawbarlineError(Exception):
    """Base for every error raised on purpose."""

    code = "drawbarline"

    def __init__(self, message: str, **context: Any) -> None:
        super().__init__(message)
        self.message = message
        self.context: dict[str, Any] = dict(context)

    def __str__(self) -> str:
        if not self.context:
            return self.message
        shown = ", ".join(f"{k}={v!r}" for k, v in sorted(self.context.items()))
        return f"{self.message} ({shown})"

    def __repr__(self) -> str:
        return f"{type(self).__name__}({self.message!r}, code={self.code!r})"


class ConfigurationError(DrawbarlineError):
    """Something the operator set up is wrong or missing."""

    code = "configuration"


class MarkError(DrawbarlineError):
    """A reporting mark or car number that cannot be what it claims to be."""

    code = "mark"


class RegistryError(DrawbarlineError):
    """The fleet register was asked for something it cannot do."""

    code = "registry"


class UnknownCar(RegistryError):
    """A car was named that the register has never heard of."""

    code = "unknown_car"


class DuplicateCar(RegistryError):
    """A car was registered twice under the same marks."""

    code = "duplicate_car"


class EventError(DrawbarlineError):
    """An event cannot be placed on a car's stream."""

    code = "event"


class RateError(DrawbarlineError):
    """No rate, or an impossible one, for the car and date asked about."""

    code = "rate"


class TariffError(DrawbarlineError):
    """The demurrage tariff cannot answer for this car."""

    code = "tariff"


class BillingError(DrawbarlineError):
    """A repair bill will not assemble."""

    code = "billing"


class SettlementError(DrawbarlineError):
    """The accounting period refuses the change."""

    code = "settlement"
