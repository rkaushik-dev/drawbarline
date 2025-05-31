import pytest
from click.testing import CliRunner

from drawbarline.cli import cli

RATES = """type_code,from_age,to_age,effective,hourly,mileage
L351,0,9,2024-07-01,4630,1210
L351,10,99,2024-07-01,3180,1210
"""

SHIPPERS = """code,name,average_agreement
ESCPAP,Escanaba Paper,no
PWRGRN,Powers Grain,yes
"""

INDUSTRIES = """code,name,station,shipper,spots,switched
ESCPAP1,mill house track,ESC,ESCPAP,11,
PWRGRN1,elevator,PWR,PWRGRN,20,mon;wed;fri
"""

CAR = [
    "fleet",
    "add",
    "CN 400100",
    "--designation",
    "LO",
    "--type-code",
    "L351",
    "--built",
    "2020-06-01",
    "--light-weight",
    "63500",
    "--gross-rail-load",
    "286000",
    "--length",
    "59-6",
]

SHEET = """car,kind,when,station,road,industry,waybill,tenths
CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
CN 400100,moved,2025-01-06 13:00,PWR,,,,336
CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
CN 400100,released,2025-01-14 15:00,PWR,,PWRGRN1,,
CN 400100,pulled,2025-01-15 17:00,PWR,,PWRGRN1,,
CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
"""


def run(database, *args):
    return CliRunner().invoke(cli, ["--database", database, *args])


@pytest.fixture
def books(tmp_path):
    database = str(tmp_path / "books.db")
    CliRunner().invoke(cli, ["--database", database, "init"])
    filings = (
        ("rates.csv", RATES),
        ("shippers.csv", SHIPPERS),
        ("industries.csv", INDUSTRIES),
    )
    for name, text in filings:
        (tmp_path / name).write_text(text, encoding="utf-8")
    run(database, "reference", "rates", str(tmp_path / "rates.csv"))
    run(database, "reference", "shippers", str(tmp_path / "shippers.csv"))
    run(database, "reference", "industries", str(tmp_path / "industries.csv"))
    run(
        database,
        "reference",
        "tariff",
        "4200",
        "--effective",
        "2024-07-01",
        "--free-loaded",
        "2",
        "--free-empty",
        "1",
        "--step",
        "2:10000",
        "--step",
        "0:20000",
    )
    run(database, *CAR)
    sheet = tmp_path / "events.csv"
    sheet.write_text(SHEET, encoding="utf-8")
    run(database, "events", "load", str(sheet))
    return database


def test_the_reference_tables_load(books):
    found = run(books, "reference", "show")
    assert "rate bands  2" in found.output
    assert "tariffs     1" in found.output
    assert "shippers    2" in found.output
    assert "industries  2" in found.output


def test_a_track_with_no_switching_days_takes_the_working_week(books):
    assert run(books, "reference", "show").exit_code == 0


def test_a_step_that_cannot_be_read_is_refused(books):
    found = run(
        books,
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


def test_a_close_reports_what_it_did(books):
    found = run(books, "close", "2025-01", "--issued", "2025-02-10")
    assert found.exit_code == 0
    assert "month      2025-01" in found.output
    assert "closed 2025-01" in found.output


def test_a_close_posts_the_car_hire(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10")
    assert "postings  1" in run(books, "status").output


def test_a_close_shuts_the_month(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10")
    assert "closed    2025-01" in run(books, "periods").output


def test_a_dry_run_changes_nothing(books):
    found = run(books, "close", "2025-01", "--issued", "2025-02-10", "--dry-run")
    assert "dry run" in found.output
    assert "nothing closed yet" in run(books, "periods").output


def test_a_dry_run_still_reports_the_numbers(books):
    found = run(books, "close", "2025-01", "--issued", "2025-02-10", "--dry-run")
    assert "car hire" in found.output


def test_closing_a_month_twice_is_refused(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10")
    assert run(books, "close", "2025-01", "--issued", "2025-02-10").exit_code != 0


def test_a_month_that_cannot_be_read_is_refused(books):
    assert run(books, "close", "january", "--issued", "2025-02-10").exit_code != 0


def test_closing_before_the_month_ends_is_refused(books):
    assert run(books, "close", "2025-01", "--issued", "2025-01-20").exit_code != 0


def test_a_statement_prints_the_two_balances(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10")
    found = run(books, "statement", "CN", "2025-01")
    assert "opening   $0.00" in found.output
    assert "we pay CN" in found.output


def test_a_road_with_nothing_posted_reads_square(books):
    found = run(books, "statement", "ELS", "2025-01")
    assert "square" in found.output


def test_a_month_is_reopened_with_a_reason(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10")
    found = run(books, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet")
    assert found.exit_code == 0
    assert "reopened  2025-01" in run(books, "periods").output


def test_reopening_a_month_that_is_open_is_refused(books):
    found = run(books, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "why not")
    assert found.exit_code != 0


def test_a_reopened_month_can_be_closed_again(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10")
    run(books, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected sheet")
    assert run(books, "close", "2025-01", "--issued", "2025-03-05").exit_code == 0


def test_the_demurrage_invoice_is_printed(books):
    found = run(books, "close", "2025-01", "--issued", "2025-02-10")
    assert "invoice    demurrage invoice" in found.output
