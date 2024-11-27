import pytest

from drawbarline.mileage import (
    BRANCH,
    MAIN,
    MILEPOSTS,
    MileageError,
    Milepost,
    milepost,
    miles_between,
    route_tenths,
    stations_on,
    tenths_between,
)
from drawbarline.reference import STATIONS


def test_every_station_on_the_line_has_a_milepost():
    assert {s.code for s in STATIONS} == set(MILEPOSTS)


def test_a_milepost_prints_its_line_and_tenths():
    assert str(Milepost(MAIN, 336)) == "M33.6"


def test_a_milepost_on_a_whole_mile_still_prints_a_tenth():
    assert str(Milepost(BRANCH, 280)) == "B28.0"


def test_a_milepost_reads_as_decimal_miles():
    assert Milepost(MAIN, 175).miles == 17.5


def test_a_line_this_road_does_not_have_is_refused():
    with pytest.raises(MileageError):
        Milepost("C", 100)


def test_a_milepost_below_zero_is_refused():
    with pytest.raises(MileageError):
        Milepost(MAIN, -1)


def test_escanaba_is_the_zero_of_the_main():
    assert milepost("ESC").tenths == 0


def test_the_branch_takes_its_own_zero_at_powers():
    assert milepost("PWR").line == MAIN
    assert milepost("IMT").line == BRANCH


def test_a_station_off_the_railroad_has_no_milepost():
    with pytest.raises(MileageError):
        milepost("CHI")


def test_two_stations_on_the_main_measure_by_difference():
    assert tenths_between("ESC", "GRB") == 1089


def test_distance_does_not_care_which_way_round():
    assert tenths_between("GRB", "ESC") == tenths_between("ESC", "GRB")


def test_a_station_to_itself_is_no_distance():
    assert tenths_between("PWR", "PWR") == 0


def test_a_move_off_the_branch_goes_by_way_of_powers():
    assert tenths_between("IMT", "ESC") == 286 + 336


def test_a_move_onto_the_branch_measures_the_same_way():
    assert tenths_between("GRB", "NIA") == 412 + (1089 - 336)


def test_two_stations_on_the_branch_measure_by_difference():
    assert tenths_between("IMT", "NIA") == 126


def test_powers_to_a_branch_station_is_just_the_branch():
    assert tenths_between("PWR", "IMT") == 286


def test_miles_read_as_decimals_for_a_report():
    assert miles_between("ESC", "GRB") == 108.9


def test_a_route_adds_up_its_legs():
    assert route_tenths(["ESC", "PWR", "IMT"]) == 336 + 286


def test_a_route_that_doubles_back_counts_both_ways():
    assert route_tenths(["ESC", "PWR", "ESC"]) == 672


def test_a_route_of_one_place_is_refused():
    with pytest.raises(MileageError):
        route_tenths(["ESC"])


def test_a_route_through_a_station_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        route_tenths(["ESC", "CHI"])


def test_the_main_runs_from_escanaba_to_green_bay():
    main = stations_on(MAIN)
    assert main[0] == "ESC"
    assert main[-1] == "GRB"


def test_the_branch_holds_two_stations():
    assert stations_on(BRANCH) == ["IMT", "NIA"]


def test_a_line_this_road_does_not_have_lists_nothing():
    with pytest.raises(MileageError):
        stations_on("C")


def test_the_stations_come_back_in_milepost_order():
    main = stations_on(MAIN)
    posts = [MILEPOSTS[code].tenths for code in main]
    assert posts == sorted(posts)
