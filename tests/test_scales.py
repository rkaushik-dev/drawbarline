from datetime import date, timedelta

import pytest

from drawbarline.lading import KRAFT_PAPER, WHEAT
from drawbarline.mileage import MileageError
from drawbarline.scales import (
    CERTIFICATION_DAYS,
    Result,
    Scale,
    ScaleError,
    ScaleTest,
    WeightAgreement,
    billing_weight,
    certified_on,
    last_test,
    require_certified,
)
from drawbarline.weight import Weight

TESTED = date(2025, 1, 6)


def a_scale(**changed):
    fields = {"code": "ESC1", "station": "ESC", "capacity": Weight(400000)}
    fields.update(changed)
    return Scale(**fields)


def a_test(on=TESTED, result=Result.PASSED, error=1, scale="ESC1"):
    return ScaleTest(scale, on, result, error, inspector="state weights and measures")


def an_agreement(**changed):
    fields = {
        "shipper": "PWRGRN",
        "commodity": WHEAT,
        "agreed": Weight(198000),
        "effective": date(2024, 7, 1),
    }
    fields.update(changed)
    return WeightAgreement(**fields)


def test_a_scale_prints_its_code_and_place():
    assert str(a_scale()) == "ESC1 at ESC"


def test_a_scale_code_is_upper_case():
    with pytest.raises(ScaleError):
        a_scale(code="esc1")


def test_a_scale_at_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        a_scale(station="CHI")


def test_a_scale_that_weighs_nothing_is_refused():
    with pytest.raises(ScaleError):
        a_scale(capacity=Weight(0))


def test_a_test_prints_its_result():
    assert str(a_test()) == "ESC1 passed on 2025-01-06"


def test_a_test_names_who_did_it():
    with pytest.raises(ScaleError):
        ScaleTest("ESC1", TESTED, Result.PASSED, 1, inspector="  ")


def test_an_error_is_stated_as_a_magnitude():
    with pytest.raises(ScaleError):
        a_test(error=-1)


def test_a_small_error_is_inside_tolerance():
    assert a_test(error=2).inside_tolerance is True


def test_a_large_error_is_outside_tolerance():
    assert a_test(error=5).inside_tolerance is False


def test_a_pass_certifies_for_a_year():
    assert a_test().certifies_until == TESTED + timedelta(days=CERTIFICATION_DAYS)


def test_a_failed_test_leaves_the_scale_unusable():
    assert Result.FAILED.usable is False
    assert Result.ADJUSTED.usable is True


def test_the_last_test_is_the_most_recent_one():
    tests = [a_test(TESTED), a_test(date(2025, 6, 1))]
    assert last_test(tests, "ESC1", date(2025, 8, 1)).tested_on == date(2025, 6, 1)


def test_a_test_after_the_day_is_not_the_last_one():
    tests = [a_test(TESTED), a_test(date(2025, 6, 1))]
    assert last_test(tests, "ESC1", date(2025, 3, 1)).tested_on == TESTED


def test_a_scale_never_tested_has_no_last_test():
    assert last_test([], "ESC1", date(2025, 3, 1)) is None


def test_a_test_on_another_scale_does_not_count():
    tests = [a_test(scale="PWR1")]
    assert last_test(tests, "ESC1", date(2025, 3, 1)) is None


def test_a_scale_inside_its_year_is_certified():
    assert certified_on([a_test()], "ESC1", date(2025, 6, 1)) is True


def test_a_scale_past_its_year_is_not_certified():
    assert certified_on([a_test()], "ESC1", date(2026, 6, 1)) is False


def test_a_scale_that_failed_its_test_is_not_certified():
    assert certified_on([a_test(result=Result.FAILED)], "ESC1", date(2025, 2, 1)) is False


def test_a_scale_adjusted_and_passed_is_certified():
    found = certified_on([a_test(result=Result.ADJUSTED)], "ESC1", date(2025, 2, 1))
    assert found is True


def test_a_scale_never_tested_is_not_certified():
    assert certified_on([], "ESC1", date(2025, 2, 1)) is False


def test_a_weight_on_a_certified_scale_stands():
    require_certified([a_test()], "ESC1", date(2025, 6, 1))


def test_a_weight_on_an_uncertified_scale_is_refused():
    with pytest.raises(ScaleError):
        require_certified([a_test()], "ESC1", date(2026, 6, 1))


def test_an_agreement_prints_the_shipper_and_the_weight():
    assert str(an_agreement()) == "PWRGRN wheat at 198,000 lb"


def test_an_agreement_with_no_shipper_is_refused():
    with pytest.raises(ScaleError):
        an_agreement(shipper="  ")


def test_an_agreed_weight_of_nothing_is_refused():
    with pytest.raises(ScaleError):
        an_agreement(agreed=Weight(0))


def test_an_agreement_that_expires_first_is_refused():
    with pytest.raises(ScaleError):
        an_agreement(expires=date(2024, 1, 1))


def test_an_agreement_applies_to_its_shipper_and_commodity():
    found = an_agreement()
    assert found.applies_to("PWRGRN", WHEAT, date(2025, 1, 14)) is True
    assert found.applies_to("ESCPAP", WHEAT, date(2025, 1, 14)) is False
    assert found.applies_to("PWRGRN", KRAFT_PAPER, date(2025, 1, 14)) is False


def test_an_expired_agreement_does_not_apply():
    found = an_agreement(expires=date(2024, 12, 31))
    assert found.applies_to("PWRGRN", WHEAT, date(2025, 1, 14)) is False


def test_the_agreement_is_the_billing_weight():
    found = billing_weight([an_agreement()], "PWRGRN", WHEAT, date(2025, 1, 14))
    assert found == Weight(198000)


def test_the_agreement_beats_the_scale():
    found = billing_weight(
        [an_agreement()], "PWRGRN", WHEAT, date(2025, 1, 14), weighed=Weight(197600)
    )
    assert found == Weight(198000)


def test_without_an_agreement_the_scale_bills():
    found = billing_weight([], "PWRGRN", WHEAT, date(2025, 1, 14), weighed=Weight(197600))
    assert found == Weight(197600)


def test_with_neither_there_is_nothing_to_bill_on():
    with pytest.raises(ScaleError):
        billing_weight([], "PWRGRN", WHEAT, date(2025, 1, 14))
