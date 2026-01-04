from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.reports.ledger import (
    balances,
    by_kind_report,
    trial_balance,
    trial_balance_report,
)
from drawbarline.settlement.ledger import Ledger, Posting, PostingKind
from drawbarline.settlement.period import Period

CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
JANUARY = Period(2025, 1)
FEBRUARY = Period(2025, 2)


def a_ledger():
    ledger = Ledger()
    ledger.post(Posting(CN, JANUARY, PostingKind.CAR_HIRE, Money(120000), JANUARY.last_day))
    ledger.post(Posting(CN, JANUARY, PostingKind.REPAIRS, Money(-45000), JANUARY.last_day))
    ledger.post(Posting(ELS, JANUARY, PostingKind.CAR_HIRE, Money(8000), JANUARY.last_day))
    return ledger


def test_a_line_carries_the_three_numbers():
    found = trial_balance(a_ledger(), JANUARY)[0]
    assert found.opening == Money(0)
    assert found.movement == Money(75000)
    assert found.closing == Money(75000)


def test_a_line_prints_the_identity():
    found = trial_balance(a_ledger(), JANUARY)[0]
    assert str(found) == "CN $0.00 + $750.00 = $750.00"


def test_every_line_balances():
    assert all(one.balances for one in trial_balance(a_ledger(), JANUARY))


def test_a_line_counts_the_postings_in_the_month():
    found = trial_balance(a_ledger(), JANUARY)[0]
    assert found.postings == 2


def test_the_second_month_opens_where_the_first_closed():
    ledger = a_ledger()
    ledger.post(Posting(CN, FEBRUARY, PostingKind.CAR_HIRE, Money(5000), FEBRUARY.last_day))
    first = trial_balance(ledger, JANUARY)[0]
    second = trial_balance(ledger, FEBRUARY)[0]
    assert second.opening == first.closing


def test_the_whole_pool_balances():
    ledger = a_ledger()
    assert balances(trial_balance(ledger, JANUARY), ledger, JANUARY) is True


def test_an_empty_ledger_balances():
    ledger = Ledger()
    assert balances(trial_balance(ledger, JANUARY), ledger, JANUARY) is True


def test_the_report_opens_with_a_heading():
    lines = trial_balance_report(a_ledger(), JANUARY)
    assert lines[0] == "TRIAL BALANCE"
    assert lines[1] == "2025-01"


def test_the_report_has_a_line_a_road():
    lines = trial_balance_report(a_ledger(), JANUARY)
    assert any(line.startswith("CN") for line in lines)
    assert any(line.startswith("ELS") for line in lines)


def test_the_report_totals_every_road():
    lines = trial_balance_report(a_ledger(), JANUARY)
    total = [line for line in lines if line.startswith("2 roads")][0]
    assert "$830.00" in total


def test_the_report_says_it_balances():
    assert trial_balance_report(a_ledger(), JANUARY)[-1] == "balances"


def test_a_month_with_nothing_in_it_still_balances():
    assert trial_balance_report(Ledger(), JANUARY)[-1] == "balances"


def test_the_kind_report_splits_the_month():
    lines = by_kind_report(a_ledger(), JANUARY)
    assert lines[0] == "POSTINGS BY KIND"
    assert any(line.startswith("car hire") for line in lines)
    assert any(line.startswith("repairs") for line in lines)


def test_the_kind_report_totals_to_the_same_number():
    kinds = by_kind_report(a_ledger(), JANUARY)
    balance = trial_balance_report(a_ledger(), JANUARY)
    assert any("$830.00" in line for line in kinds)
    assert any("$830.00" in line for line in balance)


def test_a_kind_with_nothing_posted_is_left_off():
    lines = by_kind_report(a_ledger(), JANUARY)
    assert not any(line.startswith("demurrage") for line in lines)


def test_the_kinds_come_back_in_their_published_order():
    ledger = a_ledger()
    ledger.post(Posting(CN, JANUARY, PostingKind.PAYMENT, Money(-1000), JANUARY.last_day))
    lines = by_kind_report(ledger, JANUARY)
    wanted = ("car hire", "repairs", "payment")
    rows = [line.split("  ")[0] for line in lines if line.startswith(wanted)]
    assert rows == ["car hire", "repairs", "payment"]


def test_every_line_fits_the_paper():
    for line in trial_balance_report(a_ledger(), JANUARY):
        assert len(line) <= 80


def test_a_month_before_anything_was_posted_opens_at_nothing():
    found = trial_balance(a_ledger(), Period(2024, 12))
    assert all(one.opening == Money(0) for one in found)
