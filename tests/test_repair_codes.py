import pytest

from drawbarline.errors import BillingError
from drawbarline.repairs.codes import (
    WHY_MADE,
    JobCode,
    Responsibility,
    WhyMade,
    codes_answered_by,
    responsibility_for,
    why_made,
)


def test_a_job_code_prints_its_digits():
    assert str(JobCode("4712")) == "4712"


def test_a_job_code_groups_by_its_first_two_digits():
    assert JobCode("4712").family == "47"


@pytest.mark.parametrize("bad", ["", "471", "47120", "47A2", "  "])
def test_a_job_code_that_is_not_four_digits_is_refused(bad):
    with pytest.raises(BillingError):
        JobCode(bad)


def test_a_job_code_that_is_not_text_is_refused():
    with pytest.raises(BillingError):
        JobCode(4712)


def test_a_why_made_code_carries_its_meaning():
    assert WhyMade("01").text == "worn beyond limit"


def test_a_why_made_code_prints_its_digits():
    assert str(WhyMade("03")) == "03"


def test_a_why_made_code_nobody_publishes_is_refused():
    with pytest.raises(BillingError):
        WhyMade("99")


@pytest.mark.parametrize("bad", ["", "1", "011", "A1"])
def test_a_why_made_code_that_is_not_two_digits_is_refused(bad):
    with pytest.raises(BillingError):
        WhyMade(bad)


def test_a_why_made_code_reads_back_with_space_taken_off():
    assert why_made(" 03 ") == WhyMade("03")


def test_wear_lands_on_the_owner():
    assert WhyMade("01").usual_responsibility is Responsibility.OWNER


def test_handling_damage_lands_on_the_handling_line():
    assert WhyMade("03").usual_responsibility is Responsibility.HANDLING_LINE


def test_lading_damage_lands_on_a_third_party():
    assert WhyMade("04").usual_responsibility is Responsibility.THIRD_PARTY


def test_a_wrongly_applied_part_is_joint():
    assert WhyMade("14").usual_responsibility is Responsibility.JOINT


def test_every_published_code_carries_text_and_a_party():
    for text, who in WHY_MADE.values():
        assert text
        assert isinstance(who, Responsibility)


def test_every_published_code_is_two_digits():
    assert all(len(code) == 2 and code.isdigit() for code in WHY_MADE)


def test_the_codes_one_party_answers_for_come_back_together():
    owner = codes_answered_by(Responsibility.OWNER)
    assert "01" in owner
    assert "03" not in owner


def test_the_lists_together_cover_every_code():
    gathered = set()
    for who in Responsibility:
        gathered.update(codes_answered_by(who))
    assert gathered == set(WHY_MADE)


def test_responsibility_falls_out_of_the_code_by_default():
    assert responsibility_for(WhyMade("01")) is Responsibility.OWNER


def test_an_inspector_can_say_otherwise():
    found = responsibility_for(WhyMade("01"), overridden=Responsibility.HANDLING_LINE)
    assert found is Responsibility.HANDLING_LINE


def test_the_owner_and_a_joint_finding_are_both_billable_to_the_owner():
    assert Responsibility.OWNER.billable_to_owner is True
    assert Responsibility.JOINT.billable_to_owner is True


def test_a_handling_line_finding_is_not_billed_to_the_owner():
    assert Responsibility.HANDLING_LINE.billable_to_owner is False


def test_every_party_has_readable_text():
    assert all(who.text for who in Responsibility)


def test_codes_compare_by_value_and_hash():
    assert WhyMade("01") == WhyMade("01")
    assert len({JobCode("4712"), JobCode("4712")}) == 1
