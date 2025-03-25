from datetime import date

import pytest

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.settlement.ledger import Ledger, Posting, PostingKind
from drawbarline.settlement.period import Period
from drawbarline.settlement.statement import Statement, cut, cut_all

CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
JANUARY = Period(2025, 1)
FEBRUARY = Period(2025, 2)
ISSUED = date(2025, 2, 10)


def post(ledger, road=CN, period=JANUARY, kind=PostingKind.CAR_HIRE, cents=120000, on=None):
    ledger.post(
        Posting(
            road=road,
            period=period,
            kind=kind,
            amount=Money(cents),
            on=on or period.last_day,
        )
    )


def a_ledger():
    ledger = Ledger()
    post(ledger)
    post(ledger, kind=PostingKind.REPAIRS, cents=-45000)
    post(ledger, kind=PostingKind.MILEAGE, cents=3000)
    return ledger


def test_a_statement_prints_the_road_month_and_direction():
    statement = cut(a_ledger(), CN, JANUARY, number=9001, issued=ISSUED)
    assert str(statement) == "statement 9001 CN 2025-01 $780.00 we pay CN"


def test_a_statement_numbered_below_one_is_refused():
    with pytest.raises(SettlementError):
        Statement(0, CN, JANUARY, ISSUED, Money(0), ())


def test_a_statement_cut_before_the_month_ends_is_refused():
    with pytest.raises(SettlementError):
        Statement(1, CN, JANUARY, date(2025, 1, 20), Money(0), ())


def test_a_statement_cut_on_the_last_day_of_the_month_is_allowed():
    assert Statement(1, CN, JANUARY, date(2025, 1, 31), Money(0), ()).square is True


def test_a_first_month_opens_at_nothing():
    assert cut(a_ledger(), CN, JANUARY, number=1, issued=ISSUED).opening == Money(0)


def test_the_month_movement_is_the_postings_in_it():
    statement = cut(a_ledger(), CN, JANUARY, number=1, issued=ISSUED)
    assert statement.movement == Money(78000)


def test_the_closing_balance_is_the_opening_plus_the_movement():
    statement = cut(a_ledger(), CN, JANUARY, number=1, issued=ISSUED)
    assert statement.closing == statement.opening + statement.movement


def test_the_second_month_opens_where_the_first_closed():
    ledger = a_ledger()
    post(ledger, period=FEBRUARY, cents=5000)
    first = cut(ledger, CN, JANUARY, number=1, issued=ISSUED)
    second = cut(ledger, CN, FEBRUARY, number=2, issued=date(2025, 3, 10))
    assert second.opening == first.closing


def test_a_statement_lists_one_line_a_kind():
    statement = cut(a_ledger(), CN, JANUARY, number=1, issued=ISSUED)
    assert [line.kind for line in statement.lines] == [
        PostingKind.CAR_HIRE,
        PostingKind.MILEAGE,
        PostingKind.REPAIRS,
    ]


def test_a_line_prints_its_kind_and_amount():
    statement = cut(a_ledger(), CN, JANUARY, number=1, issued=ISSUED)
    assert str(statement.lines[0]) == "car hire $1,200.00"


def test_a_kind_with_nothing_posted_reads_as_nothing():
    statement = cut(a_ledger(), CN, JANUARY, number=1, issued=ISSUED)
    assert statement.amount_for(PostingKind.DEMURRAGE) == Money(0)


def test_a_kind_with_something_posted_reads_back():
    statement = cut(a_ledger(), CN, JANUARY, number=1, issued=ISSUED)
    assert statement.amount_for(PostingKind.REPAIRS) == Money(-45000)


def test_a_road_that_owes_us_is_the_one_that_pays():
    ledger = Ledger()
    post(ledger, cents=-9000)
    statement = cut(ledger, CN, JANUARY, number=1, issued=ISSUED)
    assert statement.we_pay is False
    assert statement.direction() == "CN pays us"


def test_a_balanced_month_is_square():
    ledger = Ledger()
    post(ledger, cents=9000)
    post(ledger, kind=PostingKind.PAYMENT, cents=-9000)
    statement = cut(ledger, CN, JANUARY, number=1, issued=ISSUED)
    assert statement.square is True
    assert statement.direction() == "square"


def test_a_road_with_no_postings_gets_an_empty_statement():
    statement = cut(a_ledger(), ELS, JANUARY, number=1, issued=ISSUED)
    assert statement.lines == ()
    assert statement.closing == Money(0)


def test_every_road_with_an_account_gets_a_statement():
    ledger = a_ledger()
    post(ledger, road=ELS, cents=-8000)
    found = cut_all(ledger, JANUARY, first_number=9001, issued=ISSUED)
    assert [one.road for one in found] == [CN, ELS]


def test_statements_are_numbered_in_road_order():
    ledger = a_ledger()
    post(ledger, road=ELS, cents=-8000)
    found = cut_all(ledger, JANUARY, first_number=9001, issued=ISSUED)
    assert [one.number for one in found] == [9001, 9002]


def test_an_empty_ledger_cuts_nothing():
    assert cut_all(Ledger(), JANUARY, first_number=1, issued=ISSUED) == []


def test_a_payment_in_a_later_month_does_not_change_the_earlier_statement():
    ledger = a_ledger()
    post(ledger, period=FEBRUARY, kind=PostingKind.PAYMENT, cents=-78000)
    first = cut(ledger, CN, JANUARY, number=1, issued=ISSUED)
    assert first.closing == Money(78000)


def test_the_payment_squares_the_following_month():
    ledger = a_ledger()
    post(ledger, period=FEBRUARY, kind=PostingKind.PAYMENT, cents=-78000)
    second = cut(ledger, CN, FEBRUARY, number=2, issued=date(2025, 3, 10))
    assert second.closing == Money(0)
