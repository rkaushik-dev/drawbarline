from datetime import date

import pytest

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.settlement.adjustment import (
    Adjustment,
    Remittance,
    apply_remittance,
    outstanding,
    overpaid,
    post_adjustment,
)
from drawbarline.settlement.ledger import Ledger, Posting, PostingKind
from drawbarline.settlement.period import Period, PeriodRegister

CN = ReportingMark("CN")
JANUARY = Period(2025, 1)
FEBRUARY = Period(2025, 2)
MARCH = Period(2025, 3)


def an_adjustment(**changed):
    fields = {
        "road": CN,
        "corrects": JANUARY,
        "posted_in": MARCH,
        "amount": Money(-4500),
        "on": date(2025, 3, 12),
        "reason": "car hire on a car we never took",
    }
    fields.update(changed)
    return Adjustment(**fields)


def a_remittance(**changed):
    fields = {
        "number": 7001,
        "road": CN,
        "settles": JANUARY,
        "amount": Money(120000),
        "received_on": date(2025, 2, 20),
    }
    fields.update(changed)
    return Remittance(**fields)


def a_ledger():
    periods = PeriodRegister()
    ledger = Ledger(periods)
    ledger.post(Posting(CN, JANUARY, PostingKind.CAR_HIRE, Money(120000), JANUARY.last_day))
    return ledger, periods


def test_an_adjustment_prints_which_month_it_corrects():
    assert str(an_adjustment()) == "$45.00 from CN adjusting 2025-01"


def test_an_adjustment_the_other_way_prints_as_going_out():
    assert str(an_adjustment(amount=Money(4500))).startswith("$45.00 to CN")


def test_an_adjustment_of_nothing_is_refused():
    with pytest.raises(SettlementError):
        an_adjustment(amount=Money(0))


def test_an_adjustment_with_no_reason_is_refused():
    with pytest.raises(SettlementError):
        an_adjustment(reason="  ")


def test_an_adjustment_into_the_month_it_corrects_is_refused():
    with pytest.raises(SettlementError):
        an_adjustment(posted_in=JANUARY)


def test_an_adjustment_into_an_earlier_month_is_refused():
    with pytest.raises(SettlementError):
        an_adjustment(corrects=MARCH, posted_in=JANUARY)


def test_the_reference_names_the_month_and_the_reason():
    assert an_adjustment().reference.startswith("adjusts 2025-01: car hire")


def test_a_long_reason_is_cut_to_the_field():
    found = an_adjustment(reason="x" * 200)
    assert len(found.reference) == 60


def test_an_adjustment_goes_into_the_open_month():
    ledger, periods = a_ledger()
    periods.close(JANUARY)
    found = post_adjustment(ledger, periods, an_adjustment())
    assert found.period == MARCH
    assert found.kind is PostingKind.ADJUSTMENT


def test_an_adjustment_to_a_month_that_is_still_open_is_refused():
    ledger, periods = a_ledger()
    with pytest.raises(SettlementError):
        post_adjustment(ledger, periods, an_adjustment())


def test_an_adjustment_into_a_closed_month_is_refused():
    ledger, periods = a_ledger()
    periods.close(JANUARY)
    periods.close(MARCH)
    with pytest.raises(SettlementError):
        post_adjustment(ledger, periods, an_adjustment())


def test_the_closed_month_is_left_exactly_as_it_was():
    ledger, periods = a_ledger()
    periods.close(JANUARY)
    post_adjustment(ledger, periods, an_adjustment())
    assert ledger.account(CN).balance(JANUARY) == Money(120000)


def test_the_adjustment_moves_the_running_balance():
    ledger, periods = a_ledger()
    periods.close(JANUARY)
    post_adjustment(ledger, periods, an_adjustment())
    assert outstanding(ledger, CN, MARCH) == Money(115500)


def test_a_remittance_prints_what_it_settles():
    assert str(a_remittance()) == "remittance 7001 CN $1,200.00 for 2025-01"


def test_a_remittance_numbered_below_one_is_refused():
    with pytest.raises(SettlementError):
        a_remittance(number=0)


def test_a_remittance_for_nothing_is_refused():
    with pytest.raises(SettlementError):
        a_remittance(amount=Money(0))


def test_a_payment_before_the_month_ended_is_refused():
    with pytest.raises(SettlementError):
        a_remittance(received_on=date(2025, 1, 15))


def test_a_payment_brings_the_balance_down():
    ledger, _ = a_ledger()
    apply_remittance(ledger, a_remittance())
    assert outstanding(ledger, CN, JANUARY) == Money(0)


def test_a_payment_posts_against_the_month_it_settles():
    ledger, _ = a_ledger()
    found = apply_remittance(ledger, a_remittance())
    assert found.period == JANUARY
    assert found.kind is PostingKind.PAYMENT


def test_a_payment_carries_its_number_as_a_reference():
    ledger, _ = a_ledger()
    assert apply_remittance(ledger, a_remittance()).reference == "remittance 7001"


def test_a_payment_keeps_a_reference_it_was_given():
    ledger, _ = a_ledger()
    found = apply_remittance(ledger, a_remittance(reference="cheque 41207"))
    assert found.reference == "cheque 41207"


def test_a_part_payment_leaves_the_rest_owing():
    ledger, _ = a_ledger()
    apply_remittance(ledger, a_remittance(amount=Money(80000)))
    assert outstanding(ledger, CN, JANUARY) == Money(40000)


def test_a_road_that_pays_too_much_is_flagged():
    ledger, _ = a_ledger()
    apply_remittance(ledger, a_remittance(amount=Money(150000)))
    assert overpaid(ledger, CN, JANUARY) is True


def test_a_road_that_pays_exactly_is_not_overpaid():
    ledger, _ = a_ledger()
    apply_remittance(ledger, a_remittance())
    assert overpaid(ledger, CN, JANUARY) is False


def test_a_road_with_nothing_posted_owes_nothing():
    ledger, _ = a_ledger()
    assert outstanding(ledger, ReportingMark("ELS"), JANUARY) == Money(0)
