import pytest

from drawbarline.network import NetworkError
from drawbarline.reference import CENTRAL, EASTERN, JUNCTIONS, STATIONS, drawbar_line


@pytest.fixture(scope="module")
def line():
    return drawbar_line()


def test_the_home_road_is_the_drawbar_line(line):
    assert line.home.name == "Drawbar Line Railway"
    assert str(line.home.mark) == "DBL"


def test_three_roads_connect_with_us(line):
    assert [str(r.mark) for r in line.foreign_roads()] == ["CN", "CP", "ELS"]


def test_every_station_in_the_table_is_on_the_register(line):
    assert len(line.stations) == len(STATIONS)


def test_station_codes_are_unique():
    codes = [s.code for s in STATIONS]
    assert len(codes) == len(set(codes))


def test_the_ore_dock_end_is_on_eastern_time(line):
    assert line.station("ESC").zone == EASTERN


def test_the_wisconsin_end_is_on_central_time(line):
    assert line.station("GRB").zone == CENTRAL


def test_the_zone_seam_falls_between_bark_river_and_hermansville(line):
    assert line.station("BKR").zone == EASTERN
    assert line.station("HRM").zone == CENTRAL


def test_only_the_delta_county_stations_keep_eastern_time():
    eastern = {s.code for s in STATIONS if s.zone == EASTERN}
    assert eastern == {"ESC", "WLS", "FRD", "BKR"}


def test_the_line_runs_into_wisconsin():
    assert {s.state for s in STATIONS} == {"MI", "WI"}


def test_every_junction_in_the_table_is_open(line):
    assert len(line.junctions) == len(JUNCTIONS)


def test_the_short_line_meets_us_in_three_places(line):
    assert [j.station.code for j in line.junctions_with("ELS")] == ["ESC", "IMT", "PWR"]


def test_canadian_pacific_meets_us_only_at_green_bay(line):
    assert [j.station.code for j in line.junctions_with("CP")] == ["GRB"]


def test_a_junction_reads_as_the_road_at_the_place(line):
    assert str(line.junction("MRN/CN")) == "CN at Marinette"


def test_nobody_interchanges_at_wallace(line):
    assert line.junctions_with("CN") and not [j for j in line.junctions if j.station.code == "WAL"]


def test_a_road_we_do_not_meet_is_unknown(line):
    with pytest.raises(NetworkError):
        line.road("UP")


def test_building_the_line_twice_gives_two_independent_registers():
    first = drawbar_line()
    second = drawbar_line()
    assert first is not second
    assert [s.code for s in first.stations] == [s.code for s in second.stations]
