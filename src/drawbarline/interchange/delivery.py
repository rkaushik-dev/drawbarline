"""Whether a car can be handed across, taking everything into account.

Three separate things can stop a car at a junction and until now each was
asked separately. The equipment inspection says whether the car is fit. The
embargo register says whether anything is stopped. The interchange agreement
says whether that road takes that kind of car at that place at that hour.

A crew standing at the junction wants one answer, so this asks all three and
gives back one list. The order is the order they would be found in: the paper
on the car first, then what the railroad has stopped, then what the connection
will take.

The daily cap needs a count, and the count comes off the event log rather than
being carried anywhere, because the cars already delivered today is exactly
the sort of number that goes stale in a column.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime

from drawbarline.agreements import InterchangeAgreement, check_delivery
from drawbarline.clock import to_local
from drawbarline.embargo import EmbargoRegister
from drawbarline.events.kinds import EventKind
from drawbarline.events.stream import EventLog
from drawbarline.fleet.acceptance import inspect
from drawbarline.fleet.register import FleetRegister
from drawbarline.lading import Commodity
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.network import Station


def delivered_today(
    log: EventLog, junction: str, road: ReportingMark, day: date, station: Station
) -> int:
    """How many cars have gone across at one junction to one road on a local day."""
    return len(
        [
            event
            for event in log.at_station(junction)
            if event.kind is EventKind.DELIVERED
            and event.road == road
            and to_local(station, event.when).date() == day
        ]
    )


@dataclass(frozen=True, slots=True)
class Verdict:
    """One answer for the crew at the junction."""

    car: CarMark
    junction: str
    road: ReportingMark
    findings: tuple[str, ...]

    @property
    def goes(self) -> bool:
        return not self.findings

    def __str__(self) -> str:
        if self.goes:
            return f"{self.car} goes to {self.road} at {self.junction}"
        return f"{self.car} held at {self.junction}: {self.findings[0]}"


def can_deliver(
    car: CarMark,
    *,
    fleet: FleetRegister,
    log: EventLog,
    embargoes: EmbargoRegister,
    agreement: InterchangeAgreement,
    station: Station,
    local: datetime,
    commodity: Commodity | None = None,
) -> Verdict:
    """Everything that stops the car, in the order somebody would find it."""
    day = local.date()
    findings: list[str] = []

    findings.extend(str(one) for one in inspect(fleet, car, day))

    caught = embargoes.against(
        day, station=agreement.junction, commodity=commodity, road=agreement.road
    )
    findings.extend(f"{one} is in force" for one in caught)

    already = delivered_today(log, agreement.junction, agreement.road, day, station)
    kind = fleet.get(car).kind if fleet.has(car) else None
    if kind is not None:
        findings.extend(
            check_delivery(
                agreement,
                kind=kind,
                local=local,
                hazardous=bool(commodity and commodity.hazardous),
                already_today=already,
            )
        )

    return Verdict(car, agreement.junction, agreement.road, tuple(findings))
