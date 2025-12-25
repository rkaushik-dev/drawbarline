from datetime import date, datetime, time

import pytest

from drawbarline.agreements import (
    AgreementError,
    InterchangeAgreement,
    check_delivery,
    room_left,
    will_take,
)
from drawbarline.fleet.aar import CarKind
from drawbarline.marks import ReportingMark
from drawbarline.mileage import MileageError

CN = ReportingMark("CN")
HOPPER = CarKind.COVERED_HOPPER
TANK = CarKind.TANK
MORNING = datetime(2025, 1, 14, 9, 0)


def an_agreement(**changed):
    fields = {
        "road": CN,
        "junction": "ESC",
        "effective": date(2024, 7, 1),
        "kinds": frozenset({HOPPER, CarKind.BOX}),
        "daily_cap": 40,
        "opens": time(7, 0),
        "closes": time(19, 0),
    }
    fields.update(changed)
    return InterchangeAgreement(**fields)


def test_an_agreement_prints_its_hours_and_cap():
    assert str(an_agreement()) == "ESC/CN 07:00 to 19:00 cap 40"


def test_an_agreement_at_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        an_agreement(junction="CHI")


def test_an_agreement_taking_no_kind_is_refused():
    with pytest.raises(AgreementError):
        an_agreement(kinds=frozenset())


def test_a_cap_of_nothing_is_refused():
    with pytest.raises(AgreementError):
        an_agreement(daily_cap=0)


def test_a_junction_that_closes_before_it_opens_is_refused():
    with pytest.raises(AgreementError):
        an_agreement(opens=time(19, 0), closes=time(7, 0))


def test_an_agreement_that_expires_first_is_refused():
    with pytest.raises(AgreementError):
        an_agreement(expires=date(2024, 1, 1))


def test_an_open_agreement_runs_on():
    assert an_agreement().in_force_on(date(2099, 1, 1)) is True


def test_an_agreement_is_not_in_force_before_it_starts():
    assert an_agreement().in_force_on(date(2024, 1, 1)) is False


def test_an_expired_agreement_is_not_in_force():
    found = an_agreement(expires=date(2024, 12, 31))
    assert found.in_force_on(date(2025, 1, 14)) is False


def test_the_junction_is_manned_inside_its_hours():
    assert an_agreement().manned_at(MORNING) is True


def test_the_junction_is_closed_before_it_opens():
    assert an_agreement().manned_at(datetime(2025, 1, 14, 6, 0)) is False


def test_the_junction_is_closed_on_the_closing_hour():
    assert an_agreement().manned_at(datetime(2025, 1, 14, 19, 0)) is False


def test_an_agreement_takes_the_kinds_it_names():
    assert an_agreement().takes_kind(HOPPER) is True
    assert an_agreement().takes_kind(TANK) is False


def test_a_car_inside_the_agreement_goes_across():
    assert will_take(an_agreement(), kind=HOPPER, local=MORNING) is True


def test_a_kind_the_road_does_not_take_is_refused():
    found = check_delivery(an_agreement(), kind=TANK, local=MORNING)
    assert any("does not take a tank car" in one for one in found)


def test_hazardous_material_is_refused_unless_the_agreement_allows_it():
    found = check_delivery(an_agreement(), kind=HOPPER, local=MORNING, hazardous=True)
    assert any("hazardous" in one for one in found)


def test_an_agreement_that_allows_hazardous_takes_it():
    found = an_agreement(hazardous=True)
    assert will_take(found, kind=HOPPER, local=MORNING, hazardous=True) is True


def test_a_car_offered_when_the_junction_is_shut_is_refused():
    found = check_delivery(an_agreement(), kind=HOPPER, local=datetime(2025, 1, 14, 22, 0))
    assert any("closed at 22:00" in one for one in found)


def test_a_car_past_the_daily_cap_is_refused():
    found = check_delivery(an_agreement(), kind=HOPPER, local=MORNING, already_today=40)
    assert any("taken its 40 cars" in one for one in found)


def test_the_car_that_makes_the_cap_still_goes():
    assert will_take(an_agreement(), kind=HOPPER, local=MORNING, already_today=39) is True


def test_an_agreement_out_of_force_refuses_everything():
    found = check_delivery(an_agreement(expires=date(2024, 12, 31)), kind=HOPPER, local=MORNING)
    assert any("no agreement in force" in one for one in found)


def test_several_reasons_come_back_together():
    found = check_delivery(
        an_agreement(),
        kind=TANK,
        local=datetime(2025, 1, 14, 22, 0),
        hazardous=True,
        already_today=40,
    )
    assert len(found) == 4


def test_a_negative_count_of_cars_is_refused():
    with pytest.raises(AgreementError):
        check_delivery(an_agreement(), kind=HOPPER, local=MORNING, already_today=-1)


def test_the_room_left_comes_off_the_cap():
    assert room_left(an_agreement(), 12) == 28


def test_a_junction_at_its_cap_has_no_room():
    assert room_left(an_agreement(), 40) == 0


def test_a_junction_past_its_cap_has_no_negative_room():
    assert room_left(an_agreement(), 55) == 0


def test_a_negative_count_is_refused_when_asking_for_room():
    with pytest.raises(AgreementError):
        room_left(an_agreement(), -1)
