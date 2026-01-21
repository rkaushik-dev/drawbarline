"""Track scales, and the weights nobody actually weighs.

A track scale is a legal instrument. It has to be tested on a cycle by a
qualified inspector, and a weight taken on a scale that was out of
certification is not a weight anybody has to accept. So the scale carries its
last test and the tests carry their error, and a weight is only good if the
scale was in certification on the day.

The other half is the weights that are agreed rather than taken. A shipper
loading the same product into the same cars every day does not weigh every
car. They agree a weight with the road, it is filed, and every car of that
commodity from that shipper bills at it until somebody asks for a check
weigh. The agreement is the weight for billing, and the fact that the car on
the scale would read four hundred pounds different is not an error, it is the
agreement working.

Neither of those is a rounding convenience. Both are things a shipper can
appeal, which is why both are dated and both are on file.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from enum import Enum

from drawbarline.errors import DrawbarlineError
from drawbarline.lading import Commodity
from drawbarline.mileage import milepost
from drawbarline.weight import Weight

CERTIFICATION_DAYS = 365
TOLERANCE_TENTHS_PERCENT = 2


class ScaleError(DrawbarlineError):
    """A scale, a test or an agreed weight will not stand."""

    code = "scale"


class Result(Enum):
    """How a scale test came out."""

    PASSED = "passed"
    FAILED = "failed"
    ADJUSTED = "adjusted and passed"

    @property
    def usable(self) -> bool:
        """Whether the scale may be weighed on after this test."""
        return self is not Result.FAILED


@dataclass(frozen=True, slots=True)
class Scale:
    """One track scale."""

    code: str
    station: str
    capacity: Weight

    def __post_init__(self) -> None:
        if not self.code.isupper() or not self.code.isalnum():
            raise ScaleError("a scale code is upper case", code=self.code)
        milepost(self.station)
        if self.capacity.pounds < 1:
            raise ScaleError("a scale weighs something", code=self.code)

    def __str__(self) -> str:
        return f"{self.code} at {self.station}"


@dataclass(frozen=True, slots=True)
class ScaleTest:
    """One certification test on one scale."""

    scale: str
    tested_on: date
    result: Result
    error_tenths_percent: int = 0
    inspector: str = ""

    def __post_init__(self) -> None:
        if not self.inspector.strip():
            raise ScaleError("a test names who did it", scale=self.scale)
        if self.error_tenths_percent < 0:
            raise ScaleError("an error is stated as a magnitude", scale=self.scale)

    @property
    def inside_tolerance(self) -> bool:
        return self.error_tenths_percent <= TOLERANCE_TENTHS_PERCENT

    @property
    def certifies_until(self) -> date:
        return self.tested_on + timedelta(days=CERTIFICATION_DAYS)

    def __str__(self) -> str:
        return f"{self.scale} {self.result.value} on {self.tested_on}"


def last_test(tests: list[ScaleTest], scale: str, on: date) -> ScaleTest | None:
    """The most recent test on a scale as of a day."""
    found = [one for one in tests if one.scale == scale and one.tested_on <= on]
    if not found:
        return None
    return max(found, key=lambda one: one.tested_on)


def certified_on(tests: list[ScaleTest], scale: str, day: date) -> bool:
    """Whether a weight taken on that day would stand up."""
    found = last_test(tests, scale, day)
    if found is None:
        return False
    return found.result.usable and day <= found.certifies_until


def require_certified(tests: list[ScaleTest], scale: str, day: date) -> None:
    """Refuse a weight taken on a scale that was out of certification."""
    if not certified_on(tests, scale, day):
        raise ScaleError("that scale was not in certification", scale=scale, on=str(day))


@dataclass(frozen=True, slots=True)
class WeightAgreement:
    """A weight agreed with a shipper instead of taken on a scale."""

    shipper: str
    commodity: Commodity
    agreed: Weight
    effective: date
    expires: date | None = None

    def __post_init__(self) -> None:
        if not self.shipper.strip():
            raise ScaleError("an agreement names a shipper")
        if self.agreed.pounds < 1:
            raise ScaleError("an agreed weight is a weight", shipper=self.shipper)
        if self.expires is not None and self.expires < self.effective:
            raise ScaleError("an agreement cannot expire before it starts", shipper=self.shipper)

    def covers(self, day: date) -> bool:
        if day < self.effective:
            return False
        return self.expires is None or day <= self.expires

    def applies_to(self, shipper: str, commodity: Commodity, day: date) -> bool:
        return (
            self.shipper == shipper and self.commodity.code == commodity.code and self.covers(day)
        )

    def __str__(self) -> str:
        return f"{self.shipper} {self.commodity.name} at {self.agreed}"


def billing_weight(
    agreements: list[WeightAgreement],
    shipper: str,
    commodity: Commodity,
    day: date,
    weighed: Weight | None = None,
) -> Weight:
    """What the car bills at: the agreement if there is one, else the scale."""
    for one in agreements:
        if one.applies_to(shipper, commodity, day):
            return one.agreed
    if weighed is None:
        raise ScaleError(
            "no agreement and no weight, so nothing to bill on",
            shipper=shipper,
            commodity=str(commodity.code),
        )
    return weighed
