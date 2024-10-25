"""The Drawbar Line itself, as data.

Sixteen stations from the ore dock at Escanaba down the west shore of Green
Bay into Wisconsin, and a branch off Powers up to Iron Mountain and the state
line at Niagara. Two roads and a short line connect with us, at seven places.

None of this is inferred from anything. It changes when the road opens or
closes a junction, which is a filing, not a deployment, so it lives in one
function that builds the register and nowhere else.
"""

from __future__ import annotations

from drawbarline.marks import ReportingMark
from drawbarline.network import Network, Road, Station

EASTERN = "America/Detroit"
CENTRAL = "America/Chicago"

DRAWBAR_LINE = Road(ReportingMark("DBL"), "Drawbar Line Railway", home=True)

CONNECTIONS = (
    Road(ReportingMark("CN"), "Canadian National"),
    Road(ReportingMark("ELS"), "Escanaba and Lake Superior"),
    Road(ReportingMark("CP"), "Canadian Pacific"),
)

# Delta county keeps eastern time and Menominee county keeps central, so the
# boundary falls between Bark River and Hermansville. It is a real seam on a
# real map and every hour billed across it depends on getting it right.
STATIONS = (
    Station("ESC", "Escanaba", "MI", EASTERN),
    Station("WLS", "Wells", "MI", EASTERN),
    Station("FRD", "Ford River", "MI", EASTERN),
    Station("BKR", "Bark River", "MI", EASTERN),
    Station("HRM", "Hermansville", "MI", CENTRAL),
    Station("PWR", "Powers", "MI", CENTRAL),
    Station("SPL", "Spalding", "MI", CENTRAL),
    Station("WAL", "Wallace", "MI", CENTRAL),
    Station("STE", "Stephenson", "MI", CENTRAL),
    Station("MEN", "Menominee", "MI", CENTRAL),
    Station("MRN", "Marinette", "WI", CENTRAL),
    Station("PSH", "Peshtigo", "WI", CENTRAL),
    Station("OCO", "Oconto", "WI", CENTRAL),
    Station("GRB", "Green Bay", "WI", CENTRAL),
    Station("IMT", "Iron Mountain", "MI", CENTRAL),
    Station("NIA", "Niagara", "WI", CENTRAL),
)

JUNCTIONS = (
    ("ESC", "CN"),
    ("ESC", "ELS"),
    ("PWR", "ELS"),
    ("MRN", "CN"),
    ("GRB", "CN"),
    ("GRB", "CP"),
    ("IMT", "ELS"),
)


def drawbar_line() -> Network:
    """Build the register the way the road actually is."""
    network = Network(DRAWBAR_LINE)
    for road in CONNECTIONS:
        network.add_road(road)
    for station in STATIONS:
        network.add_station(station)
    for station_code, road_mark in JUNCTIONS:
        network.open_junction(station_code, road_mark)
    return network
