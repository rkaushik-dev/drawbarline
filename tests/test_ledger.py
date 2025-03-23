from datetime import date

import pytest

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.settlement.ledger import Account, Ledger, Posting, PostingKind
from drawbarline.settlement.period import Period, PeriodRegister

CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
JANUARY = Period(2025, 1)
FEBRUARY = Period(2025, 2)


def a_posting(**changed):
    fields = {
        "road": CN,
        "period": JANUARY,
        "kind": PostingKind.CAR_HIRE,
        "amount": Money(120000),
        "on": date(2025, 1, 31),
        "reference": "cn january",
    }
    fields.update(changed)
    return Posting(**fields)


def test_a_posting_we_owe_prints_as_going_out():
    assert str(a_posting()) == "2025-01 car hire to CN $1,200.00"


def test_a_posting_they_owe_prints_as_coming_in():
    assert str(a_posting(amount=Money(-5000))) == "2025-01 car hire from CN $50.00"


def test_the_sign_says_which_way_the_money_runs():
    assert a_posting().we_owe is True
    assert a_posting(amount=Money(-1)).they_owe is True


def test_a_posting_dated_outside_its_month_is_refused():
    with pytest.raises(SettlementError):
        a_posting(on=date(2025, 2, 3))


def test_an_adjustment_may_be_dated_later_than_its_month():
    found = a_posting(kind=PostingKind.ADJUSTMENT, on=date(2025, 4, 3))
    assert found.period == JANUARY


def test_every_kind_has_readable_text():
    assert all(kind.text for kind in PostingKind)


def test_an_account_takes_only_its_own_road():
    account = Account(CN)
    with pytest.raises(SettlementError):
        account.post(a_posting(road=ELS))


def test_an_empty_account_balances_at_nothing():
    assert Account(CN).balance() == Money(0)


def test_postings_add_up_with_their_signs():
    account = Account(CN)
    account.post(a_posting())
    account.post(a_posting(kind=PostingKind.REPAIRS, amount=Money(-45000)))
    assert account.balance() == Money(75000)


def test_a_balance_can_be_asked_for_one_month():
    account = Account(CN)
    account.post(a_posting())
    account.post(a_posting(period=FEBRUARY, on=date(2025, 2, 28), amount=Money(9000)))
    assert account.balance(JANUARY) == Money(120000)
    assert account.balance() == Money(129000)


def test_a_closing_balance_runs_through_a_month():
    account = Account(CN)
    account.post(a_posting())
    account.post(a_posting(period=FEBRUARY, on=date(2025, 2, 28), amount=Money(9000)))
    assert account.balance_through(JANUARY) == Money(120000)
    assert account.balance_through(FEBRUARY) == Money(129000)


def test_an_account_splits_by_kind():
    account = Account(CN)
    account.post(a_posting())
    account.post(a_posting(kind=PostingKind.MILEAGE, amount=Money(3000)))
    found = account.by_kind()
    assert found[PostingKind.CAR_HIRE] == Money(120000)
    assert found[PostingKind.MILEAGE] == Money(3000)


def test_postings_come_back_in_month_and_date_order():
    account = Account(CN)
    account.post(a_posting(period=FEBRUARY, on=date(2025, 2, 28)))
    account.post(a_posting(on=date(2025, 1, 3)))
    account.post(a_posting())
    assert [one.on for one in account.postings()] == [
        date(2025, 1, 3),
        date(2025, 1, 31),
        date(2025, 2, 28),
    ]


def test_a_ledger_opens_an_account_when_it_needs_one():
    ledger = Ledger()
    ledger.post(a_posting())
    assert ledger.roads() == [CN]
    assert len(ledger) == 1


def test_a_road_with_nothing_posted_reads_as_an_empty_account():
    assert Ledger().account(ELS).balance() == Money(0)


def test_a_posting_into_a_closed_month_is_refused():
    periods = PeriodRegister()
    periods.close(JANUARY)
    ledger = Ledger(periods)
    with pytest.raises(SettlementError):
        ledger.post(a_posting())


def test_a_posting_into_a_month_reopened_is_allowed_again():
    periods = PeriodRegister()
    periods.close(JANUARY)
    periods.reopen(JANUARY, date(2025, 3, 1), "corrected sheet from cn")
    ledger = Ledger(periods)
    assert ledger.post(a_posting()).amount == Money(120000)


def test_the_net_is_one_number_a_road():
    ledger = Ledger()
    ledger.post(a_posting())
    ledger.post(a_posting(kind=PostingKind.REPAIRS, amount=Money(-45000)))
    ledger.post(a_posting(road=ELS, amount=Money(-8000)))
    assert ledger.net() == {CN: Money(75000), ELS: Money(-8000)}


def test_the_two_directions_are_reported_apart():
    ledger = Ledger()
    ledger.post(a_posting())
    ledger.post(a_posting(road=ELS, amount=Money(-8000)))
    assert ledger.we_owe() == Money(120000)
    assert ledger.owed_to_us() == Money(8000)


def test_the_position_is_the_two_directions_together():
    ledger = Ledger()
    ledger.post(a_posting())
    ledger.post(a_posting(road=ELS, amount=Money(-8000)))
    assert ledger.position() == Money(112000)


def test_a_month_can_be_netted_on_its_own():
    ledger = Ledger()
    ledger.post(a_posting())
    ledger.post(a_posting(period=FEBRUARY, on=date(2025, 2, 28), amount=Money(500)))
    assert ledger.net(JANUARY) == {CN: Money(120000)}


def test_a_payment_moves_the_balance_the_other_way():
    ledger = Ledger()
    ledger.post(a_posting())
    ledger.post(a_posting(kind=PostingKind.PAYMENT, amount=Money(-120000)))
    assert ledger.net()[CN] == Money(0)


def test_an_empty_ledger_is_square():
    ledger = Ledger()
    assert ledger.position() == Money(0)
    assert ledger.roads() == []
