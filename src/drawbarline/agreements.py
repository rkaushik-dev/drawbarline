"""What each connection will take, and when.

An interchange is not a hole in a fence. It runs under an agreement that says
which kinds of car that road will accept at that junction, whether they will
take hazardous material there at all, how many cars a day they will take, and
what hours anybody is there to take them. Handing a road a car outside its
agreement is not an error the system stops, it is a car standing at a junction
overnight earning car hire.

The daily cap is the one that bites. A connection that takes forty cars a day
takes the forty first tomorrow, and the forty first is the one that has to be
picked before the train is made up rather than after it arrives.

Manned hours are stated in local time at the junction, because that is what
the agreement says and because the junctions on this railroad are not all on
the same clock.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time

from drawbarline.errors import DrawbarlineError
from drawbarline.fleet.aar import CarKind
from drawbarline.marks import ReportingMark
from drawbarline.mileage import milepost


class AgreementError(DrawbarlineError):
    """An agreement cannot stand as written."""

    code = "agreement"


@dataclass(frozen=True, slots=True)
class InterchangeAgreement:
    """What one road takes at one junction."""

    road: ReportingMark
    junction: str
    effective: date
    kinds: frozenset[CarKind]
    daily_cap: int
    opens: time
    closes: time
    hazardous: bool = False
    expires: date | None = None

    def __post_init__(self) -> None:
        milepost(self.junction)
        if not self.kinds:
            raise AgreementError(
                "an agreement that takes no kind of car is not an agreement",
                junction=self.junction,
            )
        if self.daily_cap < 1:
            raise AgreementError("a cap of nothing closes the junction", junction=self.junction)
        if self.opens >= self.closes:
            raise AgreementError("the junction opens before it closes", junction=self.junction)
        if self.expires is not None and self.expires < self.effective:
            raise AgreementError(
                "an agreement cannot expire before it starts", junction=self.junction
            )

    @property
    def code(self) -> str:
        return f"{self.junction}/{self.road}"

    def in_force_on(self, day: date) -> bool:
        if day < self.effective:
            return False
        return self.expires is None or day <= self.expires

    def manned_at(self, local: datetime) -> bool:
        return self.opens <= local.time() < self.closes

    def takes_kind(self, kind: CarKind) -> bool:
        return kind in self.kinds

    def __str__(self) -> str:
        return f"{self.code} {self.opens:%H:%M} to {self.closes:%H:%M} cap {self.daily_cap}"


def check_delivery(
    agreement: InterchangeAgreement,
    *,
    kind: CarKind,
    local: datetime,
    hazardous: bool = False,
    already_today: int = 0,
) -> list[str]:
    """Every reason the connection will not take this car right now."""
    if already_today < 0:
        raise AgreementError("a count of cars is not negative", given=already_today)
    found = []
    if not agreement.in_force_on(local.date()):
        found.append(f"{agreement.code} has no agreement in force")
    if not agreement.takes_kind(kind):
        found.append(f"{agreement.code} does not take a {kind.description}")
    if hazardous and not agreement.hazardous:
        found.append(f"{agreement.code} does not take hazardous material")
    if not agreement.manned_at(local):
        found.append(
            f"{agreement.code} is closed at {local:%H:%M}, it is manned "
            f"{agreement.opens:%H:%M} to {agreement.closes:%H:%M}"
        )
    if already_today >= agreement.daily_cap:
        found.append(f"{agreement.code} has taken its {agreement.daily_cap} cars for the day")
    return found


def will_take(
    agreement: InterchangeAgreement,
    *,
    kind: CarKind,
    local: datetime,
    hazardous: bool = False,
    already_today: int = 0,
) -> bool:
    return not check_delivery(
        agreement,
        kind=kind,
        local=local,
        hazardous=hazardous,
        already_today=already_today,
    )


def room_left(agreement: InterchangeAgreement, already_today: int) -> int:
    """How many more cars the connection will take today."""
    if already_today < 0:
        raise AgreementError("a count of cars is not negative", given=already_today)
    return max(agreement.daily_cap - already_today, 0)
