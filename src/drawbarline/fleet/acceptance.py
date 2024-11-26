"""Whether a car may be taken in interchange.

The inspector at the junction has a short list of reasons to turn a car back,
and every one of them costs somebody a day. Getting the list wrong in either
direction is expensive: refuse a good car and the connection reroutes its
traffic, accept a bad one and we own the defect from that moment.

This is the paper half of the inspection. It reads the register, the calendar
and the waybill, and it does not pretend to see the car. Anything the
inspector finds with his own eyes arrives as a defect code and is added to
what is found here.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from drawbarline.errors import UnknownCar
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark
from drawbarline.weight import Weight, overload


class Refusal(Enum):
    """A reason a car does not go across the junction."""

    NOT_REGISTERED = "no equipment record"
    RETIRED = "retired from service"
    NOT_YET_BUILT = "dated before the car existed"
    AGE = "past fifty years in interchange service"
    OVERLOADED = "lading is over the load limit"
    DEFECT = "carries a defect that has to be repaired first"

    @property
    def text(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Finding:
    """One reason, with the detail that lets somebody argue about it."""

    reason: Refusal
    detail: str = ""

    def __str__(self) -> str:
        if not self.detail:
            return self.reason.text
        return f"{self.reason.text} ({self.detail})"


def inspect(
    register: FleetRegister,
    mark: CarMark,
    day: date,
    *,
    lading: Weight | None = None,
    defects: tuple[str, ...] = (),
) -> list[Finding]:
    """Every reason to turn the car back, in the order an inspector reads them."""
    findings: list[Finding] = []
    try:
        car = register.get(mark)
    except UnknownCar:
        return [Finding(Refusal.NOT_REGISTERED, str(mark))]

    if day < car.built:
        findings.append(Finding(Refusal.NOT_YET_BUILT, str(car.built)))
    elif not register.in_service_on(mark, day):
        retired = register.retired_on(mark)
        findings.append(Finding(Refusal.RETIRED, str(retired) if retired else ""))

    if day >= car.interchange_expires:
        findings.append(Finding(Refusal.AGE, str(car.interchange_expires)))

    if lading is not None:
        past = overload(lading, car.load_limit)
        if past.pounds != 0:
            findings.append(Finding(Refusal.OVERLOADED, f"over by {past}"))

    findings.extend(Finding(Refusal.DEFECT, code) for code in defects)
    return findings


def acceptable(
    register: FleetRegister,
    mark: CarMark,
    day: date,
    *,
    lading: Weight | None = None,
    defects: tuple[str, ...] = (),
) -> bool:
    """Whether the car goes across, which is nothing more than an empty finding list."""
    return not inspect(register, mark, day, lading=lading, defects=defects)
