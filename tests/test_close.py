from datetime import date

import pytest

from drawbarline.carhire.liability import liability
from drawbarline.errors import SettlementError
from drawbarline.marks import CarMark
from drawbarline.money import Money
from drawbarline.settlement.close import (
    close_month,
    detentions_in,
    invoices_from,
    reclaims_in,
)
from drawbarline.settlement.ledger import PostingKind
from drawbarline.settlement.period import Period
from support import (
    CN,
    HOME,
    bad_ordered,
    delivered,
    moved,
    placed,
    pulled,
    received,
    released,
    repaired,
)
from support import books as a_books

JANUARY = Period(2025, 1)
ISSUED = date(2025, 2, 10)


def a_month():
    books = a_books("CN 400100", "CN 400101", "DBL 104207")
    received(books.log, "CN 400100", 6)
    moved(books.log, "CN 400100", 6, 336)
    placed(books.log, "CN 400100", 7)
    released(books.log, "CN 400100", 14)
    pulled(books.log, "CN 400100", 15)
    delivered(books.log, "CN 400100", 16)
    return books


def run(books, **changed):
    fields = {"first_invoice": 5001, "first_statement": 9001, "issued": ISSUED}
    fields.update(changed)
    return close_month(books, JANUARY, **fields)


def car_hire(books):
    return liability(books.fleet, books.log, books.rates, JANUARY.window, HOME)


def test_the_car_hire_is_gathered_for_the_month():
    found = car_hire(a_month())
    assert found.cars() == [CarMark.parse("CN 400100")]


def test_our_own_car_raises_no_car_hire():
    books = a_books("DBL 104207")
    received(books.log, "DBL 104207", 6)
    delivered(books.log, "DBL 104207", 8)
    assert car_hire(books).total() == Money(0)


def test_the_close_posts_the_car_hire_to_the_road():
    books = a_month()
    result = run(books)
    assert books.ledger.account(CN).by_kind(JANUARY)[PostingKind.CAR_HIRE] == result.car_hire


def test_the_close_shuts_the_month():
    books = a_month()
    run(books)
    assert books.periods.is_open(JANUARY) is False


def test_closing_a_month_that_is_already_shut_is_refused():
    books = a_month()
    run(books)
    with pytest.raises(SettlementError):
        run(books)


def test_a_bad_ordered_car_reclaims_its_hours():
    books = a_books("CN 400100")
    received(books.log, "CN 400100", 6)
    bad_ordered(books.log, "CN 400100", 8)
    repaired(books.log, "CN 400100", 10)
    delivered(books.log, "CN 400100", 12)
    found = reclaims_in(books, JANUARY)
    assert len(found) == 1
    assert found[0].amount.cents > 0


def test_our_own_car_reclaims_nothing():
    books = a_books("DBL 104207")
    received(books.log, "DBL 104207", 6)
    bad_ordered(books.log, "DBL 104207", 8)
    repaired(books.log, "DBL 104207", 10)
    assert reclaims_in(books, JANUARY) == []


def test_a_reclaim_is_posted_against_the_car_hire():
    books = a_books("CN 400100")
    received(books.log, "CN 400100", 6)
    bad_ordered(books.log, "CN 400100", 8)
    repaired(books.log, "CN 400100", 10)
    delivered(books.log, "CN 400100", 12)
    run(books)
    kinds = books.ledger.account(CN).by_kind(JANUARY)
    assert kinds[PostingKind.RECLAIM].cents < 0


def test_a_placement_is_counted_into_a_detention():
    found = detentions_in(a_month(), JANUARY)
    assert len(found) == 1
    assert found[0].industry == "ESCPAP1"


def test_a_placement_with_a_waybill_counts_as_loaded():
    assert detentions_in(a_month(), JANUARY)[0].loaded is True


def test_a_placement_with_no_waybill_counts_as_empty():
    books = a_books("CN 400100")
    received(books.log, "CN 400100", 6, waybill=None)
    placed(books.log, "CN 400100", 7, waybill=None)
    released(books.log, "CN 400100", 9)
    assert detentions_in(books, JANUARY)[0].loaded is False


def test_a_placement_outside_the_month_is_left_out():
    books = a_books("CN 400100")
    received(books.log, "CN 400100", 6)
    assert detentions_in(books, JANUARY) == []


def test_the_invoices_come_out_one_a_shipper():
    books = a_month()
    detentions = detentions_in(books, JANUARY)
    found = invoices_from(books, JANUARY, detentions, 5001)
    assert [one.shipper for one in found] == ["ESCPAP"]


def test_two_shippers_get_their_own_invoices_numbered_in_order():
    books = a_books("CN 400100", "CN 400101")
    received(books.log, "CN 400100", 6)
    placed(books.log, "CN 400100", 7)
    released(books.log, "CN 400100", 14)
    received(books.log, "CN 400101", 6)
    placed(books.log, "CN 400101", 7, industry="PWRGRN1")
    released(books.log, "CN 400101", 14, industry="PWRGRN1")
    detentions = detentions_in(books, JANUARY)
    found = invoices_from(books, JANUARY, detentions, 5001)
    assert [(one.number, one.shipper) for one in found] == [
        (5001, "ESCPAP"),
        (5002, "PWRGRN"),
    ]


def test_the_close_cuts_a_statement_for_every_road_with_an_account():
    result = run(a_month())
    assert [one.road for one in result.statements] == [CN]


def test_the_statement_number_starts_where_it_is_told():
    result = run(a_month())
    assert result.statements[0].number == 9001


def test_the_result_reports_the_three_totals():
    result = run(a_month())
    assert result.car_hire.cents > 0
    assert result.reclaimed == Money(0)
    assert result.demurrage.cents >= 0


def test_the_result_counts_what_it_posted():
    assert run(a_month()).posted == 1


def test_a_month_with_nothing_in_it_closes_quietly():
    books = a_books()
    result = run(books)
    assert result.posted == 0
    assert result.statements == []
    assert books.periods.is_open(JANUARY) is False


def test_running_the_same_month_twice_gives_the_same_car_hire():
    first = car_hire(a_month()).total()
    second = car_hire(a_month()).total()
    assert first == second
