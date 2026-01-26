import pytest

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.reports.annual import (
    annual,
    annual_report,
    busiest_month,
    months_of,
)
from drawbarline.settlement.ledger import Ledger, Posting, PostingKind
from drawbarline.settlement.period import Period

CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


def a_ledger():
    ledger = Ledger()
    for month, cents in ((1, 120000), (2, 90000), (3, 150000)):
        period = Period(2025, month)
        ledger.post(Posting(CN, period, PostingKind.CAR_HIRE, Money(cents), period.last_day))
    january = Period(2025, 1)
    ledger.post(Posting(CN, january, PostingKind.REPAIRS, Money(-45000), january.last_day))
    ledger.post(Posting(ELS, january, PostingKind.CAR_HIRE, Money(8000), january.last_day))
    return ledger


def test_a_year_is_twelve_months():
    assert len(months_of(2025)) == 12


def test_a_year_we_would_never_settle_is_refused():
    with pytest.raises(SettlementError):
        months_of(1700)


def test_a_line_a_month_comes_back():
    assert len(annual(a_ledger(), 2025)) == 12


def test_a_month_carries_its_kinds():
    found = annual(a_ledger(), 2025)[0]
    assert found.of(PostingKind.CAR_HIRE) == Money(128000)
    assert found.of(PostingKind.REPAIRS) == Money(-45000)


def test_a_kind_with_nothing_posted_reads_as_nothing():
    found = annual(a_ledger(), 2025)[0]
    assert found.of(PostingKind.DEMURRAGE) == Money(0)


def test_the_movement_is_the_kinds_together():
    found = annual(a_ledger(), 2025)[0]
    assert found.movement == Money(83000)


def test_a_month_counts_its_postings():
    assert annual(a_ledger(), 2025)[0].postings == 3


def test_a_month_with_nothing_in_it_moves_nothing():
    found = annual(a_ledger(), 2025)[11]
    assert found.movement == Money(0)
    assert found.postings == 0


def test_a_month_prints_its_movement():
    assert str(annual(a_ledger(), 2025)[0]) == "2025-01 $830.00"


def test_the_year_can_be_read_for_one_road():
    found = annual(a_ledger(), 2025, ELS)[0]
    assert found.of(PostingKind.CAR_HIRE) == Money(8000)


def test_a_road_with_nothing_posted_reads_as_an_empty_year():
    found = annual(a_ledger(), 2025, ReportingMark("CP"))
    assert all(one.movement == Money(0) for one in found)


def test_the_report_opens_with_a_heading():
    lines = annual_report(a_ledger(), 2025)
    assert lines[0] == "ANNUAL SUMMARY"
    assert lines[1] == "2025 all roads"


def test_the_report_names_one_road_when_asked():
    assert annual_report(a_ledger(), 2025, CN)[1] == "2025 CN"


def test_the_report_has_a_row_a_month():
    lines = annual_report(a_ledger(), 2025)
    rows = [line for line in lines if line.startswith("2025-")]
    assert len(rows) == 12


def test_the_report_totals_the_year():
    lines = annual_report(a_ledger(), 2025)
    total = [line for line in lines if line.strip().startswith("2025 ")][-1]
    assert "$3,230.00" in total


def test_the_year_total_agrees_with_the_months():
    lines = annual(a_ledger(), 2025)
    assert sum(one.movement.cents for one in lines) == 323000


def test_the_busiest_month_is_the_one_that_moved_most():
    assert busiest_month(annual(a_ledger(), 2025)).period == Period(2025, 3)


def test_a_year_with_nothing_in_it_has_no_busiest_month():
    assert busiest_month(annual(Ledger(), 2025)) is None


def test_every_line_fits_a_wide_carriage():
    for line in annual_report(a_ledger(), 2025):
        assert len(line) <= 132
