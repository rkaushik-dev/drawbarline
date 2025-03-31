from datetime import date

import pytest

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.settlement.dispute import (
    Dispute,
    DisputeRegister,
    Ground,
    Standing,
    bucket_for,
    bucket_labels,
)
from drawbarline.settlement.period import Period

CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
JANUARY = Period(2025, 1)


def a_dispute(reference=701, **changed):
    fields = {
        "reference": reference,
        "road": CN,
        "period": JANUARY,
        "ground": Ground.HOURS,
        "amount": Money(45000),
        "raised_on": date(2025, 2, 12),
    }
    fields.update(changed)
    return Dispute(**fields)


def test_a_dispute_prints_the_road_month_and_amount():
    assert str(a_dispute()) == "dispute 701 CN 2025-01 $450.00"


def test_a_dispute_numbered_below_one_is_refused():
    with pytest.raises(SettlementError):
        a_dispute(reference=0)


def test_a_dispute_for_nothing_is_refused():
    with pytest.raises(SettlementError):
        a_dispute(amount=Money(0))


def test_a_dispute_raised_before_the_month_ended_is_refused():
    with pytest.raises(SettlementError):
        a_dispute(raised_on=date(2025, 1, 15))


def test_every_ground_has_readable_text():
    assert all(ground.text for ground in Ground)


def test_a_dispute_counts_its_days():
    assert a_dispute().days_old(date(2025, 3, 14)) == 30


def test_a_date_before_the_dispute_is_refused():
    with pytest.raises(SettlementError):
        a_dispute().days_old(date(2025, 1, 1))


def test_there_are_four_buckets():
    assert bucket_labels() == ("1 to 30", "31 to 60", "61 to 90", "over 90")


def test_a_fresh_dispute_lands_in_the_first_bucket():
    assert bucket_for(0) == "1 to 30"
    assert bucket_for(30) == "1 to 30"


def test_a_month_old_dispute_lands_in_the_second():
    assert bucket_for(31) == "31 to 60"
    assert bucket_for(60) == "31 to 60"


def test_a_three_month_old_dispute_lands_in_the_last():
    assert bucket_for(90) == "over 90"
    assert bucket_for(400) == "over 90"


def test_a_raised_dispute_is_open():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    assert register.standing_of(701) is Standing.OPEN


def test_the_same_reference_twice_is_refused():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    with pytest.raises(SettlementError):
        register.raise_it(a_dispute())


def test_an_unknown_reference_is_refused():
    with pytest.raises(SettlementError):
        DisputeRegister().standing_of(701)


def test_settling_closes_it():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.settle(701)
    assert register.standing_of(701) is Standing.SETTLED
    assert register.open_disputes() == []


def test_withdrawing_closes_it_too():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.withdraw(701)
    assert register.standing_of(701) is Standing.WITHDRAWN


def test_closing_twice_is_refused():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.settle(701)
    with pytest.raises(SettlementError):
        register.withdraw(701)


def test_settling_something_never_raised_is_refused():
    with pytest.raises(SettlementError):
        DisputeRegister().settle(701)


def test_the_open_amount_adds_the_open_ones():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.raise_it(a_dispute(702, amount=Money(1000)))
    register.settle(702)
    assert register.open_amount() == Money(45000)


def test_disputes_can_be_read_for_one_road():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.raise_it(a_dispute(702, road=ELS))
    assert [one.reference for one in register.open_disputes(CN)] == [701]


def test_the_aging_puts_each_dispute_in_its_bucket():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.raise_it(
        a_dispute(
            702,
            period=Period(2025, 4),
            raised_on=date(2025, 5, 15),
            amount=Money(9000),
        )
    )
    aging = register.aging(date(2025, 6, 1))
    assert aging.bucket("1 to 30") == Money(9000)
    assert aging.bucket("over 90") == Money(45000)
    assert aging.bucket("31 to 60") == Money(0)


def test_the_aging_totals_the_open_disputes():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.raise_it(a_dispute(702, amount=Money(9000)))
    assert register.aging(date(2025, 3, 1)).total == Money(54000)


def test_a_settled_dispute_leaves_the_aging():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.settle(701)
    assert register.aging(date(2025, 3, 1)).total == Money(0)


def test_the_oldest_bucket_reads_back_on_its_own():
    register = DisputeRegister()
    register.raise_it(a_dispute(raised_on=date(2025, 2, 1)))
    assert register.aging(date(2025, 8, 1)).oldest == Money(45000)


def test_an_aging_prints_every_bucket_and_the_total():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    printed = str(register.aging(date(2025, 3, 1)))
    assert printed.startswith("1 to 30 $450.00")
    assert printed.endswith("total $450.00")


def test_a_bucket_nobody_has_is_refused():
    assert DisputeRegister().aging(date(2025, 3, 1)).total == Money(0)
    with pytest.raises(SettlementError):
        DisputeRegister().aging(date(2025, 3, 1)).bucket("next year")


def test_the_register_counts_every_dispute_ever_raised():
    register = DisputeRegister()
    register.raise_it(a_dispute())
    register.raise_it(a_dispute(702))
    register.settle(701)
    assert len(register) == 2
