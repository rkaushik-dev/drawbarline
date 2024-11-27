"""Where the stations sit, and how far it is between two of them.

Car hire has a mileage part as well as an hourly one, and the miles are
chargeable miles the car actually moved on our rail, not the distance as the
crow flies. So the road keeps a milepost for every station on each of its two
lines, and a distance is the difference between two of them.

The branch leaves the main at Powers and there is no other connection, so a
move from Iron Mountain to Green Bay is measured down the branch to Powers and
then along the main. That is not a routing algorithm, it is the shape of this
particular railroad, and writing it as a general graph search would be
pretending we do not know where our own track goes.

Mileposts are held in tenths of a mile because that is the resolution the
valuation maps carry and because a tenth of a mile at the car hire mileage
rate is real money over a fleet.
"""

from __future__ import annotations

from dataclasses import dataclass

from drawbarline.errors import DrawbarlineError

MAIN = "M"
BRANCH = "B"
JUNCTION_STATION = "PWR"

TENTHS_PER_MILE = 10


class MileageError(DrawbarlineError):
    """Two places cannot be measured between."""

    code = "mileage"


@dataclass(frozen=True, slots=True)
class Milepost:
    """A place on one of the two lines, in tenths of a mile from its zero."""

    line: str
    tenths: int

    def __post_init__(self) -> None:
        if self.line not in (MAIN, BRANCH):
            raise MileageError("this road has a main and a branch", line=self.line)
        if self.tenths < 0:
            raise MileageError("a milepost does not run below zero", tenths=self.tenths)

    @property
    def miles(self) -> float:
        return self.tenths / TENTHS_PER_MILE

    def __str__(self) -> str:
        whole, part = divmod(self.tenths, TENTHS_PER_MILE)
        return f"{self.line}{whole}.{part}"


# Escanaba is the zero of the main. The branch takes its own zero at Powers.
MILEPOSTS: dict[str, Milepost] = {
    "ESC": Milepost(MAIN, 0),
    "WLS": Milepost(MAIN, 42),
    "FRD": Milepost(MAIN, 98),
    "BKR": Milepost(MAIN, 175),
    "HRM": Milepost(MAIN, 271),
    "PWR": Milepost(MAIN, 336),
    "SPL": Milepost(MAIN, 390),
    "WAL": Milepost(MAIN, 462),
    "STE": Milepost(MAIN, 528),
    "MEN": Milepost(MAIN, 624),
    "MRN": Milepost(MAIN, 641),
    "PSH": Milepost(MAIN, 713),
    "OCO": Milepost(MAIN, 847),
    "GRB": Milepost(MAIN, 1089),
    "IMT": Milepost(BRANCH, 286),
    "NIA": Milepost(BRANCH, 412),
}


def milepost(station_code: str) -> Milepost:
    try:
        return MILEPOSTS[station_code]
    except KeyError:
        raise MileageError("no milepost for that station", station=station_code) from None


def tenths_between(first: str, second: str) -> int:
    """Track distance in tenths of a mile, by way of Powers when it has to be."""
    here = milepost(first)
    there = milepost(second)
    if here.line == there.line:
        return abs(here.tenths - there.tenths)
    junction = milepost(JUNCTION_STATION)
    on_branch = here if here.line == BRANCH else there
    on_main = there if here.line == BRANCH else here
    return on_branch.tenths + abs(on_main.tenths - junction.tenths)


def miles_between(first: str, second: str) -> float:
    return tenths_between(first, second) / TENTHS_PER_MILE


def route_tenths(stations: list[str]) -> int:
    """The distance over a sequence of stations, which is what a movement records."""
    if len(stations) < 2:
        raise MileageError("a route needs at least two places", stations=list(stations))
    return sum(
        tenths_between(here, there) for here, there in zip(stations, stations[1:], strict=False)
    )


def stations_on(line: str) -> list[str]:
    """Every station on one line, in milepost order."""
    if line not in (MAIN, BRANCH):
        raise MileageError("this road has a main and a branch", line=line)
    found = [code for code, post in MILEPOSTS.items() if post.line == line]
    return sorted(found, key=lambda code: MILEPOSTS[code].tenths)
