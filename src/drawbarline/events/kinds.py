"""The kinds of thing that happen to a car.

There are not many of them and the list closes. Every hour of car hire and
every day of demurrage on this railroad is the gap between two of these, so a
kind that nobody can start or stop an obligation with does not belong here and
a kind that is missing shows up as an argument with a connection.

Each kind carries what it demands. An interchange has to name the road on the
other side of it, a placement has to name the track, and a movement has to
carry the distance. Those are not conventions, they are the reason the event
can be billed, so they are checked where the event is built and not where it
is used.
"""

from __future__ import annotations

from enum import Enum

from drawbarline.errors import EventError


class EventKind(Enum):
    """Everything that can be recorded against a car."""

    RECEIVED = "received in interchange"
    DELIVERED = "delivered in interchange"
    MOVED = "moved between stations"
    NOTIFIED = "consignee notified of arrival"
    PLACED = "placed at an industry"
    CONSTRUCTIVE = "constructively placed"
    RELEASED = "released by the shipper"
    PULLED = "pulled off the industry track"
    BAD_ORDERED = "set out bad order"
    REPAIRED = "released from repair"
    HELD = "held under embargo"
    FREED = "released from hold"
    WEIGHED = "weighed"

    @property
    def text(self) -> str:
        return self.value

    @property
    def needs_road(self) -> bool:
        """An interchange is with somebody, and the somebody has to be named."""
        return self in _WITH_ROAD

    @property
    def needs_industry(self) -> bool:
        return self in _WITH_INDUSTRY

    @property
    def needs_distance(self) -> bool:
        return self is EventKind.MOVED

    @property
    def starts_car_hire(self) -> bool:
        """Our liability to the owner begins when we take the car."""
        return self is EventKind.RECEIVED

    @property
    def ends_car_hire(self) -> bool:
        return self is EventKind.DELIVERED

    @property
    def starts_demurrage(self) -> bool:
        """Free time begins at placement, actual or constructive."""
        return self in _PLACEMENTS

    @property
    def ends_demurrage(self) -> bool:
        return self is EventKind.RELEASED

    @property
    def is_placement(self) -> bool:
        return self in _PLACEMENTS

    @property
    def is_interchange(self) -> bool:
        return self in _WITH_ROAD

    def __str__(self) -> str:
        return self.name.lower()


_WITH_ROAD = frozenset({EventKind.RECEIVED, EventKind.DELIVERED})
_WITH_INDUSTRY = frozenset(
    {
        EventKind.PLACED,
        EventKind.CONSTRUCTIVE,
        EventKind.RELEASED,
        EventKind.PULLED,
    }
)
_PLACEMENTS = frozenset({EventKind.PLACED, EventKind.CONSTRUCTIVE})


def kind_named(name: str) -> EventKind:
    """Read a kind off a file or a command line."""
    try:
        return EventKind[name.strip().upper()]
    except KeyError:
        raise EventError("no event kind by that name", name=name) from None
