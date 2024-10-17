"""Roads, stations and the junctions where cars change hands.

Three things need names before anything can be billed. A road is a railroad,
named by its reporting mark. A station is a place on our line where a car can
be set out, and it carries a time zone because the hour a car was placed is
local time at that station and nowhere else. A junction is a station where a
foreign road hands us cars or takes them.

The time zone is not decoration. Our line crosses the boundary between Eastern
and Central at the Delta and Menominee county line, so a car received at
Escanaba at 09:00 and delivered at Marinette at 09:00 the same morning was on
our rail for an hour. Every clerk on this railroad has been caught by that at
least once, and the register is the only place to fix it.
"""

from __future__ import annotations

from dataclasses import dataclass
from zoneinfo import ZoneInfo

from drawbarline.errors import ConfigurationError, DrawbarlineError
from drawbarline.marks import ReportingMark


class NetworkError(DrawbarlineError):
    """A place or a road is not on the register."""

    code = "network"


@dataclass(frozen=True, slots=True)
class Road:
    """A railroad, ours or somebody else's."""

    mark: ReportingMark
    name: str
    home: bool = False

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise ConfigurationError("a road needs a name", mark=str(self.mark))
        if self.mark.is_private:
            raise ConfigurationError("a road does not carry a private mark", mark=str(self.mark))

    def __str__(self) -> str:
        return f"{self.mark} {self.name}"


@dataclass(frozen=True, slots=True)
class Station:
    """A place on our line, with the time zone its clock runs on."""

    code: str
    name: str
    state: str
    zone: str

    def __post_init__(self) -> None:
        if not self.code.isupper() or not self.code.isalnum():
            raise ConfigurationError(
                "a station code is upper case letters and digits", code=self.code
            )
        if self.state not in {"MI", "WI"}:
            raise ConfigurationError("this line runs in Michigan and Wisconsin", state=self.state)
        try:
            ZoneInfo(self.zone)
        except Exception as bad:
            raise ConfigurationError(
                "unknown time zone", station=self.code, zone=self.zone
            ) from bad

    @property
    def tz(self) -> ZoneInfo:
        return ZoneInfo(self.zone)

    def __str__(self) -> str:
        return f"{self.name}, {self.state}"


@dataclass(frozen=True, slots=True)
class Junction:
    """A station where a foreign road hands cars over or takes them away."""

    station: Station
    road: Road

    def __post_init__(self) -> None:
        if self.road.home:
            raise ConfigurationError("a junction is with a foreign road", station=self.station.code)

    @property
    def code(self) -> str:
        return f"{self.station.code}/{self.road.mark}"

    def __str__(self) -> str:
        return f"{self.road.mark} at {self.station.name}"


class Network:
    """Everything the road knows about where it goes and who it meets."""

    def __init__(self, home: Road) -> None:
        if not home.home:
            raise ConfigurationError("the home road has to say it is home", mark=str(home.mark))
        self.home = home
        self._roads: dict[str, Road] = {str(home.mark): home}
        self._stations: dict[str, Station] = {}
        self._junctions: dict[str, Junction] = {}

    def add_road(self, road: Road) -> Road:
        key = str(road.mark)
        if key in self._roads:
            raise NetworkError("that road is already on the register", mark=key)
        self._roads[key] = road
        return road

    def add_station(self, station: Station) -> Station:
        if station.code in self._stations:
            raise NetworkError("that station is already on the register", code=station.code)
        self._stations[station.code] = station
        return station

    def open_junction(self, station_code: str, road_mark: str) -> Junction:
        junction = Junction(self.station(station_code), self.road(road_mark))
        if junction.code in self._junctions:
            raise NetworkError("that junction is already open", code=junction.code)
        self._junctions[junction.code] = junction
        return junction

    def road(self, mark: str) -> Road:
        try:
            return self._roads[mark]
        except KeyError:
            raise NetworkError("no such road", mark=mark) from None

    def station(self, code: str) -> Station:
        try:
            return self._stations[code]
        except KeyError:
            raise NetworkError("no such station", code=code) from None

    def junction(self, code: str) -> Junction:
        try:
            return self._junctions[code]
        except KeyError:
            raise NetworkError("no such junction", code=code) from None

    def junctions_with(self, road_mark: str) -> list[Junction]:
        return sorted(
            (j for j in self._junctions.values() if str(j.road.mark) == road_mark),
            key=lambda j: j.station.code,
        )

    @property
    def roads(self) -> list[Road]:
        return sorted(self._roads.values(), key=lambda r: str(r.mark))

    @property
    def stations(self) -> list[Station]:
        return sorted(self._stations.values(), key=lambda s: s.code)

    @property
    def junctions(self) -> list[Junction]:
        return sorted(self._junctions.values(), key=lambda j: j.code)

    def foreign_roads(self) -> list[Road]:
        return [road for road in self.roads if not road.home]
