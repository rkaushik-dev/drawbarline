from datetime import date

import pytest

from drawbarline.embargo import Embargo, EmbargoError, EmbargoRegister
from drawbarline.lading import KRAFT_PAPER, WHEAT
from drawbarline.marks import ReportingMark
from drawbarline.mileage import MileageError

CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


def an_embargo(number=101, **changed):
    fields = {
        "number": number,
        "effective": date(2025, 1, 6),
        "reason": "bridge out at hermansville",
        "station": "HRM",
    }
    fields.update(changed)
    return Embargo(**fields)


def test_an_embargo_prints_what_it_stops():
    assert str(an_embargo()) == "embargo 101 at HRM"


def test_an_embargo_on_a_commodity_prints_the_family():
    found = an_embargo(station=None, commodity_family="01")
    assert str(found) == "embargo 101 on 01"


def test_an_embargo_against_a_road_prints_it():
    found = an_embargo(station=None, road=CN)
    assert str(found) == "embargo 101 with CN"


def test_an_embargo_numbered_below_one_is_refused():
    with pytest.raises(EmbargoError):
        an_embargo(number=0)


def test_an_embargo_with_no_reason_is_refused():
    with pytest.raises(EmbargoError):
        an_embargo(reason="  ")


def test_an_embargo_that_restricts_nothing_is_refused():
    with pytest.raises(EmbargoError) as caught:
        an_embargo(station=None)
    assert "whole railroad" in caught.value.message


def test_an_embargo_at_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        an_embargo(station="CHI")


def test_a_commodity_family_that_is_not_digits_is_refused():
    with pytest.raises(EmbargoError):
        an_embargo(station=None, commodity_family="grain")


def test_an_embargo_that_expires_before_it_starts_is_refused():
    with pytest.raises(EmbargoError):
        an_embargo(expires=date(2025, 1, 1))


def test_an_open_embargo_runs_on():
    found = an_embargo()
    assert found.in_force_on(date(2025, 1, 6)) is True
    assert found.in_force_on(date(2099, 1, 1)) is True


def test_an_embargo_is_not_in_force_before_it_starts():
    assert an_embargo().in_force_on(date(2025, 1, 5)) is False


def test_an_embargo_stops_on_the_day_after_it_expires():
    found = an_embargo(expires=date(2025, 1, 20))
    assert found.in_force_on(date(2025, 1, 20)) is True
    assert found.in_force_on(date(2025, 1, 21)) is False


def test_a_station_embargo_catches_that_station():
    assert an_embargo().catches(station="HRM") is True


def test_a_station_embargo_lets_other_stations_by():
    assert an_embargo().catches(station="ESC") is False


def test_a_commodity_embargo_catches_the_family():
    found = an_embargo(station=None, commodity_family="01")
    assert found.catches(commodity=WHEAT) is True
    assert found.catches(commodity=KRAFT_PAPER) is False


def test_a_commodity_embargo_catches_nothing_without_a_commodity():
    found = an_embargo(station=None, commodity_family="01")
    assert found.catches(station="ESC") is False


def test_a_road_embargo_catches_only_that_road():
    found = an_embargo(station=None, road=CN)
    assert found.catches(road=CN) is True
    assert found.catches(road=ELS) is False


def test_every_stated_field_has_to_match():
    found = an_embargo(commodity_family="01")
    assert found.catches(station="HRM", commodity=WHEAT) is True
    assert found.catches(station="HRM", commodity=KRAFT_PAPER) is False
    assert found.catches(station="ESC", commodity=WHEAT) is False


def test_an_embargo_goes_on_the_register():
    register = EmbargoRegister()
    register.issue(an_embargo())
    assert register.get(101).station == "HRM"
    assert len(register) == 1


def test_the_same_number_twice_is_refused():
    register = EmbargoRegister()
    register.issue(an_embargo())
    with pytest.raises(EmbargoError):
        register.issue(an_embargo())


def test_an_embargo_nobody_issued_is_refused():
    with pytest.raises(EmbargoError):
        EmbargoRegister().get(101)


def test_lifting_an_embargo_ends_it():
    register = EmbargoRegister()
    register.issue(an_embargo())
    register.lift(101, date(2025, 1, 20))
    assert register.get(101).in_force_on(date(2025, 1, 21)) is False


def test_lifting_something_with_an_end_already_is_refused():
    register = EmbargoRegister()
    register.issue(an_embargo(expires=date(2025, 1, 20)))
    with pytest.raises(EmbargoError):
        register.lift(101, date(2025, 1, 15))


def test_lifting_before_it_took_effect_is_refused():
    register = EmbargoRegister()
    register.issue(an_embargo())
    with pytest.raises(EmbargoError):
        register.lift(101, date(2025, 1, 1))


def test_lifting_something_never_issued_is_refused():
    with pytest.raises(EmbargoError):
        EmbargoRegister().lift(101, date(2025, 1, 20))


def test_the_live_embargoes_come_back_in_number_order():
    register = EmbargoRegister()
    register.issue(an_embargo(102, station="ESC"))
    register.issue(an_embargo())
    assert [one.number for one in register.in_force_on(date(2025, 1, 10))] == [101, 102]


def test_an_expired_embargo_is_not_live():
    register = EmbargoRegister()
    register.issue(an_embargo(expires=date(2025, 1, 8)))
    assert register.in_force_on(date(2025, 1, 10)) == []


def test_the_embargoes_against_one_movement_come_back():
    register = EmbargoRegister()
    register.issue(an_embargo())
    register.issue(an_embargo(102, station="ESC"))
    register.issue(an_embargo(103, station=None, commodity_family="01"))
    found = register.against(date(2025, 1, 10), station="HRM", commodity=WHEAT)
    assert [one.number for one in found] == [101, 103]


def test_a_movement_that_runs_into_nothing_comes_back_clear():
    register = EmbargoRegister()
    register.issue(an_embargo())
    assert register.against(date(2025, 1, 10), station="ESC") == []


def test_permits_are_off_unless_the_embargo_allows_them():
    assert an_embargo().permits_allowed is False
    assert an_embargo(permits_allowed=True).permits_allowed is True


def test_a_lifted_embargo_keeps_whether_it_allowed_permits():
    register = EmbargoRegister()
    register.issue(an_embargo(permits_allowed=True))
    assert register.lift(101, date(2025, 1, 20)).permits_allowed is True
