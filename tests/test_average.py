from datetime import date

import pytest

from drawbarline.demurrage.average import (
    AverageAgreement,
    AverageLedger,
    Entry,
    entry_for,
)
from drawbarline.demurrage.counting import Detention
from drawbarline.errors import TariffError
from drawbarline.marks import CarMark
from drawbarline.money import Money

CAR = CarMark.parse("CN 400100")
AGREEMENT = AverageAgreement("PWRGRN", 12000, date(2024, 7, 1))


def a_detention(chargeable=0, counted_to=date(2025, 1, 15), clock_starts=date(2025, 1, 15)):
    return Detention(
        car=CAR,
        industry="PWRGRN1",
        placed_on=date(2025, 1, 14),
        clock_starts=clock_starts,
        free_until=date(2025, 1, 16),
        counted_to=counted_to,
        chargeable=chargeable,
        loaded=True,
        constructive=False,
    )


def test_an_agreement_prints_its_shipper_and_rate():
    assert str(AGREEMENT) == "PWRGRN at $120.00 a debit from 2024-07-01"


def test_an_agreement_covers_days_from_its_start():
    assert AGREEMENT.covers(date(2024, 6, 30)) is False
    assert AGREEMENT.covers(date(2024, 7, 1)) is True


def test_an_open_agreement_covers_everything_after():
    assert AGREEMENT.covers(date(2099, 1, 1)) is True


def test_an_expired_agreement_stops_covering():
    ending = AverageAgreement("PWRGRN", 12000, date(2024, 7, 1), date(2025, 6, 30))
    assert ending.covers(date(2025, 6, 30)) is True
    assert ending.covers(date(2025, 7, 1)) is False


def test_an_agreement_that_expires_before_it_starts_is_refused():
    with pytest.raises(TariffError):
        AverageAgreement("PWRGRN", 12000, date(2025, 7, 1), date(2024, 7, 1))


def test_an_agreement_that_pays_the_customer_is_refused():
    with pytest.raises(TariffError):
        AverageAgreement("PWRGRN", -1, date(2024, 7, 1))


def test_a_car_turned_back_on_the_first_free_day_earns_a_credit():
    assert entry_for(a_detention()).credits == 1


def test_a_car_turned_back_early_earns_a_credit():
    early = a_detention(counted_to=date(2025, 1, 14))
    assert entry_for(early).credits == 1


def test_a_car_held_into_free_time_earns_nothing():
    lazy = a_detention(counted_to=date(2025, 1, 16))
    assert entry_for(lazy).credits == 0
    assert entry_for(lazy).debits == 0


def test_a_chargeable_day_is_a_debit():
    assert entry_for(a_detention(chargeable=3)).debits == 3


def test_a_car_never_earns_a_credit_and_a_debit_together():
    with pytest.raises(TariffError):
        Entry(CAR, "PWRGRN1", 1, 1)


def test_an_entry_prints_its_credit():
    assert str(Entry(CAR, "PWRGRN1", 1, 0)) == "CN 400100 1 credit"


def test_an_entry_prints_its_debits():
    assert str(Entry(CAR, "PWRGRN1", 0, 3)) == "CN 400100 3 debits"


def test_a_ledger_outside_its_agreement_is_refused():
    with pytest.raises(TariffError):
        AverageLedger(AGREEMENT, date(2024, 1, 1))


def test_an_empty_month_settles_at_nothing():
    settled = AverageLedger(AGREEMENT, date(2025, 1, 1)).settle()
    assert settled.amount == Money(0)
    assert settled.credits == 0


def test_credits_cancel_debits_one_for_one():
    ledger = AverageLedger(AGREEMENT, date(2025, 1, 1))
    ledger.post(a_detention())
    ledger.post(a_detention())
    ledger.post(a_detention(chargeable=3))
    settled = ledger.settle()
    assert settled.net_debits == 1
    assert settled.amount == Money(12000)


def test_more_credits_than_debits_leaves_nothing_owed():
    ledger = AverageLedger(AGREEMENT, date(2025, 1, 1))
    for _ in range(5):
        ledger.post(a_detention())
    ledger.post(a_detention(chargeable=2))
    settled = ledger.settle()
    assert settled.amount == Money(0)
    assert settled.net_debits == 0


def test_spare_credits_are_reported_as_wasted():
    ledger = AverageLedger(AGREEMENT, date(2025, 1, 1))
    for _ in range(5):
        ledger.post(a_detention())
    ledger.post(a_detention(chargeable=2))
    assert ledger.settle().wasted_credits == 3


def test_a_month_with_no_credits_pays_every_debit():
    ledger = AverageLedger(AGREEMENT, date(2025, 1, 1))
    ledger.post(a_detention(chargeable=4))
    assert ledger.settle().amount == Money(48000)


def test_the_ledger_counts_what_it_posted():
    ledger = AverageLedger(AGREEMENT, date(2025, 1, 1))
    ledger.post(a_detention())
    ledger.post(a_detention(chargeable=1))
    assert len(ledger) == 2
    assert len(ledger.entries) == 2


def test_the_ledger_totals_both_sides():
    ledger = AverageLedger(AGREEMENT, date(2025, 1, 1))
    ledger.post(a_detention())
    ledger.post(a_detention(chargeable=3))
    assert ledger.credits == 1
    assert ledger.debits == 3


def test_a_settlement_prints_both_sides_and_the_money():
    ledger = AverageLedger(AGREEMENT, date(2025, 1, 1))
    ledger.post(a_detention())
    ledger.post(a_detention(chargeable=3))
    assert str(ledger.settle()) == "PWRGRN 1 credits 3 debits $240.00"


def test_a_new_month_starts_with_no_credits():
    first = AverageLedger(AGREEMENT, date(2025, 1, 1))
    for _ in range(5):
        first.post(a_detention())
    second = AverageLedger(AGREEMENT, date(2025, 2, 1))
    second.post(a_detention(chargeable=2))
    assert second.settle().amount == Money(24000)
