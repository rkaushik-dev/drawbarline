import pytest

from drawbarline.clearance import (
    CHART,
    Clearance,
    ClearanceError,
    Dimensions,
    Plate,
    check_route,
    clearance_at,
    clears_route,
    tightest_on,
)
from drawbarline.measure import Length
from drawbarline.mileage import MileageError

ORDINARY = Dimensions(Length.of(15, 4), Length.of(10, 6))
TALL = Dimensions(Length.of(18, 0), Length.of(10, 6))
WIDE = Dimensions(Length.of(15, 0), Length.of(12, 4))
HUGE = Dimensions(Length.of(21, 6), Length.of(13, 2))


def test_the_plates_climb_in_height():
    heights = [plate.height.inches for plate in Plate]
    assert heights == sorted(heights)


def test_a_plate_holds_what_fits_inside_it():
    assert Plate.C.holds(ORDINARY) is True
    assert Plate.B.holds(ORDINARY) is False


def test_a_load_finds_the_smallest_plate_that_holds_it():
    assert ORDINARY.smallest_plate() is Plate.C


def test_a_short_load_takes_the_smallest_plate():
    assert Dimensions(Length.of(14, 0), Length.of(10, 0)).smallest_plate() is Plate.B


def test_a_tall_load_takes_a_bigger_plate():
    assert TALL.smallest_plate() is Plate.H


def test_a_load_outside_every_plate_is_dimensional():
    assert HUGE.smallest_plate() is None
    assert HUGE.dimensional is True


def test_an_ordinary_load_is_not_dimensional():
    assert ORDINARY.dimensional is False


def test_a_wide_load_is_dimensional_however_short():
    assert WIDE.dimensional is True


def test_a_load_prints_its_two_dimensions():
    assert str(ORDINARY) == "15'4\" high 10'6\" wide"


def test_a_load_of_no_height_is_refused():
    with pytest.raises(ClearanceError):
        Dimensions(Length(0), Length.of(10, 0))


def test_a_clearance_prints_what_it_is():
    assert str(CHART["BKR"]) == "BKR (highway overpass) 17'4\" over 12'6\" wide"


def test_a_clearance_at_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        Clearance("CHI", Length.of(20, 0), Length.of(12, 0))


def test_a_station_with_nothing_filed_reads_as_open():
    found = clearance_at("PWR")
    assert found.what == "no restriction"
    assert found.clears(TALL) is True


def test_a_station_with_a_restriction_reads_it_back():
    assert clearance_at("BKR").overhead == Length.of(17, 4)


def test_asking_about_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        clearance_at("CHI")


def test_an_ordinary_load_clears_the_whole_railroad():
    assert clears_route(ORDINARY, ["ESC", "BKR", "PWR", "GRB"]) is True


def test_a_tall_load_does_not_clear_bark_river():
    found = check_route(TALL, ["ESC", "BKR", "PWR"])
    assert len(found) == 1
    assert found[0].startswith("BKR clears")


def test_a_wide_load_does_not_clear_wallace():
    found = check_route(WIDE, ["PWR", "WAL", "MEN"])
    assert any("wide" in one for one in found)


def test_a_load_that_is_both_draws_two_findings_at_one_place():
    found = check_route(HUGE, ["BKR"])
    assert len(found) == 2


def test_a_route_avoiding_the_restriction_clears():
    assert clears_route(TALL, ["PWR", "SPL", "MEN"]) is True


def test_the_tightest_place_on_a_route_is_the_lowest_overhead():
    assert tightest_on(["ESC", "BKR", "MRN"]).station == "BKR"


def test_a_route_with_no_restriction_reports_an_open_clearance():
    assert tightest_on(["ESC", "PWR"]).what == "no restriction"


def test_an_empty_route_is_refused():
    with pytest.raises(ClearanceError):
        tightest_on([])


def test_checking_an_empty_route_is_refused():
    with pytest.raises(ClearanceError):
        check_route(ORDINARY, [])


def test_every_filed_clearance_is_at_a_station_we_serve():
    for station, one in CHART.items():
        assert one.station == station


def test_a_load_exactly_on_the_clearance_passes():
    exact = Dimensions(CHART["BKR"].overhead, Length.of(10, 0))
    assert clears_route(exact, ["BKR"]) is True


def test_a_load_one_inch_over_does_not():
    over = Dimensions(CHART["BKR"].overhead + Length(1), Length.of(10, 0))
    assert clears_route(over, ["BKR"]) is False
