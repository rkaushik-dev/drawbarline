import pytest

from drawbarline.lading import (
    IRON_ORE_PELLETS,
    KRAFT_PAPER,
    SODIUM_HYDROXIDE,
    WHEAT,
    Commodity,
    Lading,
    LadingError,
    Stcc,
)
from drawbarline.weight import Weight


def test_a_code_prints_in_the_groups_a_tariff_uses():
    assert str(Stcc("0113710")) == "01 137 10"


def test_a_code_reads_back_with_dashes_taken_out():
    assert Stcc.parse("01-137-10") == Stcc("0113710")


def test_a_code_reads_back_with_spaces_taken_out():
    assert Stcc.parse(" 01 137 10 ") == Stcc("0113710")


@pytest.mark.parametrize("bad", ["", "011371", "01137100", "011371X"])
def test_a_code_that_is_not_seven_digits_is_refused(bad):
    with pytest.raises(LadingError):
        Stcc(bad)


def test_a_code_that_is_not_text_is_refused():
    with pytest.raises(LadingError):
        Stcc(113710)


def test_the_major_group_is_the_first_two_digits():
    assert Stcc("0113710").major_group == "01"


def test_the_hazardous_group_is_forty_nine():
    assert Stcc("4930040").hazardous is True


def test_an_ordinary_commodity_is_not_hazardous():
    assert Stcc("0113710").hazardous is False


def test_a_code_reads_at_any_level():
    code = Stcc("0113710")
    assert code.at_level(1) == "0"
    assert code.at_level(4) == "0113"
    assert code.at_level(7) == "0113710"


@pytest.mark.parametrize("level", [0, 8, -1])
def test_a_level_outside_the_code_is_refused(level):
    with pytest.raises(LadingError):
        Stcc("0113710").at_level(level)


def test_a_code_sits_inside_its_family():
    assert Stcc("0113710").within("01") is True
    assert Stcc("0113710").within("0113") is True


def test_a_code_does_not_sit_inside_another_family():
    assert Stcc("0113710").within("24") is False


def test_a_family_that_is_not_digits_is_refused():
    with pytest.raises(LadingError):
        Stcc("0113710").within("grain")


def test_a_family_longer_than_a_code_is_refused():
    with pytest.raises(LadingError):
        Stcc("0113710").within("01137100")


def test_a_commodity_prints_its_code_and_name():
    assert str(WHEAT) == "01 137 10 wheat"


def test_a_commodity_needs_a_name():
    with pytest.raises(LadingError):
        Commodity(Stcc("0113710"), "  ")


def test_a_commodity_takes_its_hazard_from_its_code():
    assert SODIUM_HYDROXIDE.hazardous is True
    assert KRAFT_PAPER.hazardous is False


def test_bulk_commodities_are_marked():
    assert IRON_ORE_PELLETS.bulk is True
    assert KRAFT_PAPER.bulk is False


def test_a_lading_prints_the_commodity_and_the_weight():
    assert str(Lading(WHEAT, Weight(198000))) == "wheat 198,000 lb"


def test_a_lading_takes_its_hazard_from_the_commodity():
    assert Lading(SODIUM_HYDROXIDE, Weight(180000)).hazardous is True


def test_a_lading_of_nothing_is_refused():
    with pytest.raises(LadingError):
        Lading(WHEAT, Weight(0))


def test_the_named_commodities_all_carry_valid_codes():
    for commodity in (WHEAT, KRAFT_PAPER, IRON_ORE_PELLETS, SODIUM_HYDROXIDE):
        assert len(commodity.code.digits) == 7


def test_the_named_commodities_have_distinct_codes():
    codes = {WHEAT.code, KRAFT_PAPER.code, IRON_ORE_PELLETS.code, SODIUM_HYDROXIDE.code}
    assert len(codes) == 4
