from datetime import date

import pytest

from drawbarline.fleet.ownership import Lease, Ownership, OwnershipError
from drawbarline.marks import ReportingMark

DBL = ReportingMark("DBL")
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


def test_a_lease_prints_its_term():
    term = Lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    assert str(term) == "CN from 2025-01-01 to 2025-06-30"


def test_an_open_lease_prints_as_open():
    assert str(Lease(CN, date(2025, 1, 1))) == "CN from 2025-01-01 to open"


def test_a_lease_that_ends_before_it_begins_is_refused():
    with pytest.raises(OwnershipError):
        Lease(CN, date(2025, 6, 30), date(2025, 1, 1))


def test_a_lease_covers_both_of_its_end_days():
    term = Lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    assert term.covers(date(2025, 1, 1)) is True
    assert term.covers(date(2025, 6, 30)) is True


def test_a_lease_does_not_cover_the_day_after_it_ends():
    term = Lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    assert term.covers(date(2025, 7, 1)) is False


def test_an_open_lease_covers_everything_after_it_begins():
    term = Lease(CN, date(2025, 1, 1))
    assert term.covers(date(2099, 1, 1)) is True
    assert term.covers(date(2024, 12, 31)) is False


def test_an_unleased_car_pays_its_owner():
    assert Ownership(DBL).holder_on(date(2025, 3, 1)) == DBL


def test_a_leased_car_pays_the_lessee():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    assert owned.holder_on(date(2025, 3, 1)) == CN


def test_a_car_pays_its_owner_again_once_the_lease_runs_out():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    assert owned.holder_on(date(2025, 7, 1)) == DBL


def test_an_owner_does_not_lease_from_itself():
    with pytest.raises(OwnershipError):
        Ownership(DBL).lease(DBL, date(2025, 1, 1))


def test_two_terms_that_do_not_touch_both_stand():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    owned.lease(ELS, date(2025, 7, 1), date(2025, 12, 31))
    assert owned.holder_on(date(2025, 8, 1)) == ELS
    assert len(owned.terms) == 2


def test_two_terms_that_overlap_are_refused():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    with pytest.raises(OwnershipError):
        owned.lease(ELS, date(2025, 6, 30), date(2025, 12, 31))


def test_a_term_inside_an_open_one_is_refused():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1))
    with pytest.raises(OwnershipError):
        owned.lease(ELS, date(2026, 1, 1), date(2026, 6, 30))


def test_a_term_before_an_open_one_still_stands():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 7, 1))
    owned.lease(ELS, date(2025, 1, 1), date(2025, 6, 30))
    assert owned.holder_on(date(2025, 3, 1)) == ELS
    assert owned.holder_on(date(2026, 3, 1)) == CN


def test_terms_come_back_in_the_order_they_run():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 7, 1), date(2025, 12, 31))
    owned.lease(ELS, date(2025, 1, 1), date(2025, 6, 30))
    assert [t.lessee for t in owned.terms] == [ELS, CN]


def test_closing_the_open_term_ends_it():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1))
    owned.end_open_term(date(2025, 3, 31))
    assert owned.holder_on(date(2025, 4, 1)) == DBL


def test_closing_when_nothing_is_open_is_refused():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    with pytest.raises(OwnershipError):
        owned.end_open_term(date(2025, 3, 31))


def test_a_closed_term_leaves_room_for_the_next():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1))
    owned.end_open_term(date(2025, 3, 31))
    owned.lease(ELS, date(2025, 4, 1))
    assert owned.holder_on(date(2025, 5, 1)) == ELS


def test_a_car_knows_whether_it_is_out_on_lease():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    assert owned.leased_on(date(2025, 3, 1)) is True
    assert owned.leased_on(date(2025, 8, 1)) is False


def test_the_term_on_a_day_comes_back_whole():
    owned = Ownership(DBL)
    owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    assert owned.term_on(date(2025, 3, 1)).lessee == CN
    assert owned.term_on(date(2025, 8, 1)) is None
