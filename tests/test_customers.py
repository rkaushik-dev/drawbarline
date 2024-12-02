from datetime import date

import pytest

from drawbarline.customers import CustomerError, CustomerRegister, Industry, Shipper
from drawbarline.errors import ConfigurationError

MILL = Shipper("ESCPAP", "Escanaba Paper")
ELEVATOR = Shipper("PWRGRN", "Powers Grain", average_agreement=True)


def an_industry(**changed):
    fields = {
        "code": "ESCPAP1",
        "name": "mill house track",
        "station": "ESC",
        "shipper": MILL,
        "spots": 11,
    }
    fields.update(changed)
    return Industry(**fields)


def a_register():
    register = CustomerRegister()
    register.add_shipper(MILL)
    register.add_shipper(ELEVATOR)
    register.add_industry(an_industry())
    register.add_industry(an_industry(code="ESCPAP2", name="mill north", spots=6))
    register.add_industry(an_industry(code="PWRGRN1", station="PWR", shipper=ELEVATOR, spots=20))
    return register


def test_a_shipper_prints_its_code_and_name():
    assert str(MILL) == "ESCPAP Escanaba Paper"


def test_a_shipper_code_is_upper_case():
    with pytest.raises(ConfigurationError):
        Shipper("escpap", "Escanaba Paper")


def test_a_shipper_needs_a_name():
    with pytest.raises(ConfigurationError):
        Shipper("ESCPAP", " ")


def test_an_industry_prints_its_name_and_station():
    assert str(an_industry()) == "ESCPAP1 mill house track at ESC"


def test_an_industry_holds_at_least_one_car():
    with pytest.raises(ConfigurationError):
        an_industry(spots=0)


def test_an_industry_code_is_upper_case():
    with pytest.raises(ConfigurationError):
        an_industry(code="escpap1")


def test_an_industry_is_switched_weekdays_by_default():
    track = an_industry()
    assert track.switched_on(date(2025, 1, 14)) is True
    assert track.switched_on(date(2025, 1, 18)) is False


def test_an_industry_can_be_switched_on_a_saturday():
    track = an_industry(switched=frozenset({"mon", "wed", "sat"}))
    assert track.switched_on(date(2025, 1, 18)) is True
    assert track.switched_on(date(2025, 1, 14)) is False


def test_a_day_that_is_not_a_day_is_refused():
    with pytest.raises(ConfigurationError):
        an_industry(switched=frozenset({"mon", "funday"}))


def test_an_industry_nobody_switches_is_refused():
    with pytest.raises(ConfigurationError):
        an_industry(switched=frozenset())


def test_a_track_with_room_is_not_full():
    assert an_industry(spots=11).full(10) is False


def test_a_track_at_its_spot_capacity_is_full():
    assert an_industry(spots=11).full(11) is True


def test_a_track_over_its_spot_capacity_is_full():
    assert an_industry(spots=11).full(14) is True


def test_a_shipper_goes_on_and_comes_back():
    assert a_register().shipper("ESCPAP") == MILL


def test_the_same_shipper_twice_is_refused():
    register = a_register()
    with pytest.raises(CustomerError):
        register.add_shipper(MILL)


def test_an_unknown_shipper_is_refused():
    with pytest.raises(CustomerError):
        a_register().shipper("NOBODY")


def test_an_industry_goes_on_and_comes_back():
    assert a_register().industry("PWRGRN1").station == "PWR"


def test_the_same_industry_twice_is_refused():
    register = a_register()
    with pytest.raises(CustomerError):
        register.add_industry(an_industry())


def test_an_industry_whose_shipper_is_not_filed_is_refused():
    register = CustomerRegister()
    with pytest.raises(CustomerError):
        register.add_industry(an_industry())


def test_an_unknown_industry_is_refused():
    with pytest.raises(CustomerError):
        a_register().industry("NOWHERE")


def test_industries_come_back_by_station():
    assert [t.code for t in a_register().at_station("ESC")] == ["ESCPAP1", "ESCPAP2"]


def test_a_station_we_serve_nobody_at_lists_nothing():
    assert a_register().at_station("WAL") == []


def test_industries_come_back_by_shipper():
    assert [t.code for t in a_register().for_shipper("PWRGRN")] == ["PWRGRN1"]


def test_asking_for_an_unknown_shippers_track_is_refused():
    with pytest.raises(CustomerError):
        a_register().for_shipper("NOBODY")


def test_shippers_on_an_average_agreement_come_back_together():
    assert [s.code for s in a_register().on_average_agreement()] == ["PWRGRN"]


def test_the_register_iterates_industries_in_code_order():
    assert [t.code for t in a_register()] == ["ESCPAP1", "ESCPAP2", "PWRGRN1"]


def test_the_register_counts_its_industries():
    assert len(a_register()) == 3
