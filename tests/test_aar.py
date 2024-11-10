import pytest

from drawbarline.fleet.aar import (
    CarKind,
    CarTypeCode,
    EquipmentError,
    MechanicalDesignation,
    agrees,
)


def test_every_kind_has_a_single_letter():
    assert all(len(kind.letter) == 1 for kind in CarKind)


def test_kind_letters_are_unique():
    letters = [kind.letter for kind in CarKind]
    assert len(letters) == len(set(letters))


def test_every_kind_describes_itself():
    assert all(kind.description for kind in CarKind)


def test_a_kind_reads_back_off_its_letter():
    assert CarKind.from_letter("T") is CarKind.TANK


def test_a_letter_no_kind_carries_is_refused():
    with pytest.raises(EquipmentError):
        CarKind.from_letter("Z")


def test_maintenance_equipment_carries_no_lading():
    assert CarKind.MAINTENANCE.carries_lading is False
    assert CarKind.BOX.carries_lading is True


def test_a_plain_box_car_designation():
    designation = MechanicalDesignation("XM")
    assert designation.kind is CarKind.BOX
    assert designation.qualifiers == "M"
    assert str(designation) == "XM"


def test_a_one_letter_designation_has_no_qualifiers():
    assert MechanicalDesignation("F").qualifiers == ""


def test_a_covered_hopper_designation():
    assert MechanicalDesignation("LO").kind is CarKind.COVERED_HOPPER


def test_parse_takes_lower_case_and_space():
    assert MechanicalDesignation.parse(" rbl ") == MechanicalDesignation("RBL")


@pytest.mark.parametrize("bad", ["", "XMLOR", "X1", "xm", "Z"])
def test_a_designation_that_cannot_be_one_is_refused(bad):
    with pytest.raises(EquipmentError):
        MechanicalDesignation(bad)


def test_a_designation_that_is_not_text_is_refused():
    with pytest.raises(EquipmentError):
        MechanicalDesignation(12)


def test_a_type_code_carries_a_kind_and_a_detail():
    code = CarTypeCode("T104")
    assert code.kind is CarKind.TANK
    assert code.detail == 104
    assert str(code) == "T104"


def test_a_detail_with_leading_zeros_reads_as_a_number():
    assert CarTypeCode("X002").detail == 2


def test_parse_takes_lower_case():
    assert CarTypeCode.parse("l351") == CarTypeCode("L351")


@pytest.mark.parametrize("bad", ["", "T10", "T1045", "TT04", "Z104", "1104"])
def test_a_type_code_that_cannot_be_one_is_refused(bad):
    with pytest.raises(EquipmentError):
        CarTypeCode(bad)


def test_a_type_code_that_is_not_text_is_refused():
    with pytest.raises(EquipmentError):
        CarTypeCode(104)


def test_the_two_identifiers_agree_when_they_name_the_same_kind():
    assert agrees(MechanicalDesignation("LO"), CarTypeCode("L351")) is True


def test_the_two_identifiers_disagree_when_they_do_not():
    assert agrees(MechanicalDesignation("XM"), CarTypeCode("T104")) is False


def test_identifiers_compare_by_value():
    assert CarTypeCode("T104") == CarTypeCode("T104")
    assert MechanicalDesignation("XM") == MechanicalDesignation("XM")


def test_identifiers_are_hashable():
    assert len({CarTypeCode("T104"), CarTypeCode("T104"), CarTypeCode("L351")}) == 2
