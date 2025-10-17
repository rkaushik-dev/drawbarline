import pytest

from conftest import run

SHEET = """car,kind,when,station,road,industry,waybill,tenths
CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
CN 400100,moved,2025-01-06 13:00,PWR,,,,336
CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
CN 400100,released,2025-01-14 15:00,PWR,,PWRGRN1,,
CN 400100,pulled,2025-01-15 17:00,PWR,,PWRGRN1,,
CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
"""


@pytest.fixture
def worked(books, tmp_path):
    """The loaded railroad with one car worked through january."""
    sheet = tmp_path / "events.csv"
    sheet.write_text(SHEET, encoding="utf-8")
    run(books, "events", "load", str(sheet))
    return books


def test_the_reference_tables_load(worked):
    found = run(worked, "reference", "show")
    assert "rate bands  2" in found.output
    assert "tariffs     1" in found.output
    assert "shippers    2" in found.output
    assert "industries  2" in found.output


def test_a_track_with_no_switching_days_takes_the_working_week(worked):
    assert run(worked, "reference", "show").exit_code == 0


def test_a_step_that_cannot_be_read_is_refused(worked):
    found = run(
        worked,
        "reference",
        "tariff",
        "4300",
        "--effective",
        "2025-07-01",
        "--free-loaded",
        "1",
        "--free-empty",
        "1",
        "--step",
        "nonsense",
    )
    assert found.exit_code != 0


def test_a_close_reports_what_it_did(worked):
    found = run(worked, "close", "2025-01", "--issued", "2025-02-10")
    assert found.exit_code == 0
    assert "month      2025-01" in found.output
    assert "closed 2025-01" in found.output


def test_a_close_posts_the_car_hire(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10")
    assert "postings  1" in run(worked, "status").output


def test_a_close_shuts_the_month(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10")
    assert "closed    2025-01" in run(worked, "periods").output


def test_a_dry_run_changes_nothing(worked):
    found = run(worked, "close", "2025-01", "--issued", "2025-02-10", "--dry-run")
    assert "dry run" in found.output
    assert "nothing closed yet" in run(worked, "periods").output


def test_a_dry_run_still_reports_the_numbers(worked):
    found = run(worked, "close", "2025-01", "--issued", "2025-02-10", "--dry-run")
    assert "car hire" in found.output


def test_closing_a_month_twice_is_refused(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10")
    assert run(worked, "close", "2025-01", "--issued", "2025-02-10").exit_code != 0


def test_a_month_that_cannot_be_read_is_refused(worked):
    assert run(worked, "close", "january", "--issued", "2025-02-10").exit_code != 0


def test_closing_before_the_month_ends_is_refused(worked):
    assert run(worked, "close", "2025-01", "--issued", "2025-01-20").exit_code != 0


def test_a_statement_prints_the_two_balances(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10")
    found = run(worked, "statement", "CN", "2025-01")
    assert "opening   $0.00" in found.output
    assert "we pay CN" in found.output


def test_a_road_with_nothing_posted_reads_square(worked):
    found = run(worked, "statement", "ELS", "2025-01")
    assert "square" in found.output


def test_a_month_is_reopened_with_a_reason(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10")
    found = run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet")
    assert found.exit_code == 0
    assert "reopened  2025-01" in run(worked, "periods").output


def test_reopening_a_month_that_is_open_is_refused(worked):
    found = run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "why not")
    assert found.exit_code != 0


def test_a_reopened_month_can_be_closed_again(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10")
    run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet")
    assert run(worked, "close", "2025-01", "--issued", "2025-03-05").exit_code == 0


def test_the_demurrage_invoice_is_printed(worked):
    found = run(worked, "close", "2025-01", "--issued", "2025-02-10")
    assert "invoice    demurrage invoice" in found.output
