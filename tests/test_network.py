import pytest

from drawbarline.errors import ConfigurationError
from drawbarline.marks import ReportingMark
from drawbarline.network import Junction, Network, NetworkError, Road, Station

HOME = Road(ReportingMark("DBL"), "Drawbar Line Railway", home=True)
CN = Road(ReportingMark("CN"), "Canadian National")
WC = Road(ReportingMark("WC"), "Wisconsin Central")

ESCANABA = Station("ESC", "Escanaba", "MI", "America/Detroit")
POWERS = Station("PWR", "Powers", "MI", "America/Chicago")
MARINETTE = Station("MRN", "Marinette", "WI", "America/Chicago")


def a_network():
    net = Network(HOME)
    net.add_road(CN)
    net.add_road(WC)
    for station in (ESCANABA, POWERS, MARINETTE):
        net.add_station(station)
    return net


def test_a_road_prints_its_mark_and_name():
    assert str(CN) == "CN Canadian National"


def test_a_road_needs_a_name():
    with pytest.raises(ConfigurationError):
        Road(ReportingMark("CN"), "   ")


def test_a_road_does_not_carry_a_private_mark():
    with pytest.raises(ConfigurationError):
        Road(ReportingMark("PLMX"), "Pullman Leasing")


def test_a_station_prints_its_name_and_state():
    assert str(ESCANABA) == "Escanaba, MI"


def test_a_station_code_is_upper_case():
    with pytest.raises(ConfigurationError):
        Station("esc", "Escanaba", "MI", "America/Detroit")


def test_a_station_off_the_line_is_refused():
    with pytest.raises(ConfigurationError):
        Station("CHI", "Chicago", "IL", "America/Chicago")


def test_a_station_with_an_unknown_zone_is_refused():
    with pytest.raises(ConfigurationError):
        Station("ESC", "Escanaba", "MI", "America/Nowhere")


def test_the_line_crosses_a_time_zone_boundary():
    assert ESCANABA.tz.key == "America/Detroit"
    assert POWERS.tz.key == "America/Chicago"


def test_a_junction_names_the_road_and_the_place():
    junction = Junction(ESCANABA, CN)
    assert str(junction) == "CN at Escanaba"
    assert junction.code == "ESC/CN"


def test_a_junction_with_our_own_road_is_refused():
    with pytest.raises(ConfigurationError):
        Junction(ESCANABA, HOME)


def test_the_home_road_has_to_say_it_is_home():
    with pytest.raises(ConfigurationError):
        Network(CN)


def test_the_home_road_is_on_the_register_from_the_start():
    assert a_network().road("DBL") is HOME


def test_a_road_added_twice_is_refused():
    net = a_network()
    with pytest.raises(NetworkError):
        net.add_road(CN)


def test_a_station_added_twice_is_refused():
    net = a_network()
    with pytest.raises(NetworkError):
        net.add_station(ESCANABA)


def test_an_unknown_road_is_refused():
    with pytest.raises(NetworkError):
        a_network().road("BNSF")


def test_an_unknown_station_is_refused():
    with pytest.raises(NetworkError):
        a_network().station("XXX")


def test_an_unknown_junction_is_refused():
    with pytest.raises(NetworkError):
        a_network().junction("ESC/CN")


def test_opening_a_junction_puts_it_on_the_register():
    net = a_network()
    junction = net.open_junction("ESC", "CN")
    assert net.junction("ESC/CN") == junction


def test_opening_the_same_junction_twice_is_refused():
    net = a_network()
    net.open_junction("ESC", "CN")
    with pytest.raises(NetworkError):
        net.open_junction("ESC", "CN")


def test_a_junction_at_an_unknown_station_is_refused():
    net = a_network()
    with pytest.raises(NetworkError):
        net.open_junction("XXX", "CN")


def test_junctions_with_one_road_come_back_in_station_order():
    net = a_network()
    net.open_junction("MRN", "CN")
    net.open_junction("ESC", "CN")
    net.open_junction("PWR", "WC")
    assert [j.station.code for j in net.junctions_with("CN")] == ["ESC", "MRN"]


def test_the_foreign_roads_leave_out_our_own():
    assert [str(r.mark) for r in a_network().foreign_roads()] == ["CN", "WC"]


def test_the_registers_come_back_sorted():
    net = a_network()
    net.open_junction("PWR", "WC")
    net.open_junction("ESC", "CN")
    assert [s.code for s in net.stations] == ["ESC", "MRN", "PWR"]
    assert [j.code for j in net.junctions] == ["ESC/CN", "PWR/WC"]
    assert [str(r.mark) for r in net.roads] == ["CN", "DBL", "WC"]
